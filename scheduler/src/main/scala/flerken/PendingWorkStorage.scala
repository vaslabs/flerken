package flerken

import java.util.UUID

import akka.actor.typed.eventstream.EventStream.Publish
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import cats.data.NonEmptyList
import flerken.PendingWorkStorage.Retry
import AkkaExtras._
import flerken.ResultStorage.WaitForResult
import flerken.protocol.Protocol.{WorkId, WorkerId}
object PendingWorkStorage {

  def behavior(storageConfig: StorageConfig, resultStorage: ActorRef[ResultStorage.Protocol]): Behavior[Protocol] = Behaviors.setup[Protocol] { ctx =>
    val allocatedWorkStorage =
      ctx.spawn(AllocatedWorkStorage.behavior, "AllocatedWorkStorage")
    handleNoWorkBehavior(allocatedWorkStorage, storageConfig, resultStorage)
  }

  private def handleNoWorkBehavior(
            allocatedWorkStorage: ActorRef[AllocatedWorkStorage.Protocol],
            storageConfig: StorageConfig, resultStorage: ActorRef[ResultStorage.Protocol]): Behavior[Protocol] =
    Behaviors.receive[Protocol] {
      case (_, FetchWork(replyTo)) =>
        replyTo ! NoWork
        Behaviors.same
      case (ctx, AddWork(work, replyTo)) =>
        val identifier = WorkId(UUID.randomUUID())
        ctx.log.info(s"Asking result storage to wait for result $identifier")
        resultStorage ! WaitForResult(identifier, ctx.self)
        replyTo ! WorkReceived(identifier)
        ctx.scheduleOnce(storageConfig.staleTimeout, ctx.self, ExpireWork(identifier))

        behaviorWithWork(
          NonEmptyList.of(PendingWork(identifier, work)),
          allocatedWorkStorage,
          storageConfig,
          resultStorage)
      case (ctx, WorkFailed(uuid)) =>
        allocatedWorkStorage ! AllocatedWorkStorage.FetchWork(uuid, ctx.self)
        Behaviors.same
      case (ctx, Retry(id, work)) =>
        ctx.system.eventStream ! Publish(WorkRetry(id))
        behaviorWithWork(
          NonEmptyList.of(PendingWork(id, work)),
          allocatedWorkStorage,
          storageConfig,
          resultStorage)
      case (ctx, CompleteWork(identifier)) =>
        ctx.log.info(s"Removing completed work $identifier")
        allocatedWorkStorage ! AllocatedWorkStorage.RemoveWork(identifier)
        Behaviors.same
      case (_, ExpireWork(_)) =>
        Behaviors.same
      case (ctx, WorkAllocationTimeout(id)) =>
        allocatedWorkStorage ! AllocatedWorkStorage.WorkTimeout(id, ctx.self)
        Behaviors.same
  }

  def behaviorWithWork(pendingWork: NonEmptyList[PendingWork[_]],
                        allocatedWorkStorage: ActorRef[AllocatedWorkStorage.Protocol],
                        storageConfig: StorageConfig, resultStorage: ActorRef[ResultStorage.Protocol]): Behavior[Protocol] =
    List[PartialFunction[(ActorContext[Protocol], Protocol), Behavior[Protocol]]](
      handleReceivingWork(pendingWork, allocatedWorkStorage, storageConfig, resultStorage),
      fetchWorkHandling(pendingWork, allocatedWorkStorage, storageConfig, resultStorage)
    ).toBehavior

  private def handleReceivingWork(
                  pendingWork: NonEmptyList[PendingWork[_]],
                  allocatedWorkStorage: ActorRef[AllocatedWorkStorage.Protocol],
                  storageConfig: StorageConfig,
                  resultStorage: ActorRef[ResultStorage.Protocol]): PartialFunction[(ActorContext[Protocol], Protocol), Behavior[Protocol]] =
     {
      case (ctx, AddWork(work, replyTo)) =>
        val identifier = WorkId(UUID.randomUUID())
        replyTo ! WorkReceived(identifier)

        ctx.scheduleOnce(
          storageConfig.staleTimeout,
          ctx.self,
          ExpireWork(identifier)
        )
        val newWorkQueue = pendingWork :+ PendingWork(identifier, work)
        if (newWorkQueue.size >= storageConfig.highWatermark) {
          ctx.system.eventStream ! Publish(HighWatermarkReached(storageConfig.identifier))
          behaviorWithHighWatermark(newWorkQueue, allocatedWorkStorage, storageConfig, resultStorage)
        }
        else
          behaviorWithWork(
            newWorkQueue,
            allocatedWorkStorage,
            storageConfig,
            resultStorage
          )
      case (ctx, Retry(id, work)) =>
        ctx.system.eventStream ! Publish(WorkRetry(id))
        behaviorWithWork(pendingWork :+ PendingWork(id, work), allocatedWorkStorage, storageConfig, resultStorage)
    }


  private def behaviorWithHighWatermark(
                   workQueue: NonEmptyList[PendingWorkStorage.PendingWork[_]],
                   allocatedWorkStorage: ActorRef[AllocatedWorkStorage.Protocol],
                   config: StorageConfig, resultStorage: ActorRef[ResultStorage.Protocol]): Behavior[Protocol] = List(
    highWatermarkHandler(workQueue, allocatedWorkStorage, config, resultStorage),
    fetchWorkHandling(workQueue, allocatedWorkStorage, config, resultStorage),
    manageAllocatedWorkStorageBehavior(allocatedWorkStorage)
  ).toBehavior

  private def highWatermarkHandler(
              workQueue: NonEmptyList[PendingWorkStorage.PendingWork[_]],
              allocatedWorkStorage: ActorRef[AllocatedWorkStorage.Protocol],
              config: StorageConfig, resultStorage: ActorRef[ResultStorage.Protocol]): PartialHandlingWithContext[Protocol] = {
    case (_, AddWork(_, replyTo)) =>
      replyTo ! WorkRejected
      Behaviors.same
    case (ctx, Retry(id, work)) =>
      ctx.system.eventStream ! Publish(WorkRetry(id))
      behaviorWithHighWatermark(workQueue :+ PendingWork(id, work), allocatedWorkStorage, config, resultStorage)
  }

  private def fetchWorkHandling(
                                  pendingWork: NonEmptyList[PendingWork[_]],
                                  allocatedWorkStorage: ActorRef[AllocatedWorkStorage.Protocol],
                                  storageConfig: StorageConfig,
                                  resultStorage: ActorRef[ResultStorage.Protocol]): PartialHandlingWithContext[Protocol] = {
      case (ctx, FetchWork(replyTo)) =>
        val pendingWorkHead = pendingWork.head
        replyTo ! DoWork(pendingWorkHead.id, pendingWorkHead.work)
        ctx.scheduleOnce(
          storageConfig.workCompletionTimeout,
          ctx.self,
          WorkAllocationTimeout(pendingWorkHead.id)
        )
        allocatedWorkStorage ! AllocatedWorkStorage.WorkAllocated(
          pendingWorkHead.id,
          pendingWorkHead.work
        )
        NonEmptyList.fromList(pendingWork.tail)
          .map(remainingWork =>
            if (remainingWork.size >= storageConfig.highWatermark)
              behaviorWithHighWatermark(remainingWork, allocatedWorkStorage, storageConfig, resultStorage)
            else
              behaviorWithWork(remainingWork, allocatedWorkStorage, storageConfig, resultStorage)
          )
          .getOrElse(handleNoWorkBehavior(allocatedWorkStorage, storageConfig, resultStorage))
      case (ctx, ExpireWork(id)) =>
        val remainingWork = pendingWork.filterNot(_.id == id)
        if (remainingWork.size < pendingWork.size)
          ctx.system.eventStream ! Publish(PendingWorkExpired(id))
        NonEmptyList.fromList(remainingWork)
          .map(remainingWork =>
            if (remainingWork.size >= storageConfig.highWatermark)
              behaviorWithHighWatermark(remainingWork, allocatedWorkStorage, storageConfig, resultStorage)
            else
              behaviorWithWork(remainingWork, allocatedWorkStorage, storageConfig, resultStorage))
          .getOrElse(handleNoWorkBehavior(allocatedWorkStorage, storageConfig, resultStorage))
    }

  private def manageAllocatedWorkStorageBehavior(
                  allocatedWorkStorage: ActorRef[AllocatedWorkStorage.Protocol]): PartialHandlingWithContext[Protocol] = {
    case (ctx, CompleteWork(identifier)) =>
      ctx.log.info(s"Removing completed work $identifier")
      allocatedWorkStorage ! AllocatedWorkStorage.RemoveWork(identifier)
      Behaviors.same
    case (ctx, WorkFailed(identifier)) =>
      allocatedWorkStorage ! AllocatedWorkStorage.FetchWork(identifier, ctx.self)
      Behaviors.same
    case (ctx, WorkAllocationTimeout(id)) =>
      allocatedWorkStorage ! AllocatedWorkStorage.WorkTimeout(id, ctx.self)
      Behaviors.same
  }

  private case class PendingWork[W](id: WorkId, work: W)

  sealed trait Protocol

  case class FetchWork(replyTo: ActorRef[Work]) extends Protocol

  case class AddWork[W](work: W, replyTo: ActorRef[WorkAck]) extends Protocol

  case class WorkFailed(id: WorkId) extends Protocol
  case class CompleteWork(id: WorkId) extends Protocol

  private[flerken] case class Retry[W](id: WorkId, work: W) extends Protocol
  private case class ExpireWork(id: WorkId) extends Protocol
  private case class WorkAllocationTimeout(id: WorkId) extends Protocol

  sealed trait Work

  case object NoWork extends Work

  case class DoWork[W](id: WorkId, work: W) extends Work

  sealed trait WorkAck
  case class WorkReceived(id: WorkId) extends WorkAck
  case object WorkRejected extends WorkAck

  sealed trait Event

  case class WorkRetry(id: WorkId) extends Event
  case class WorkCompleted(id: WorkId) extends Event
  case class PendingWorkExpired(id: WorkId) extends Event
  case class WorkCompletionTimedOut(id: WorkId) extends Event
  case class HighWatermarkReached(worker: WorkerId) extends Event

}

private object AllocatedWorkStorage {

  def behavior: Behavior[Protocol] = Behaviors.receive[Protocol] {
    case (_, FetchWork(_, _)) =>
      Behaviors.same
    case (_, WorkAllocated(id, work)) =>
      behaviorWithAllocatedWork(Map(id -> work))
    case (_, RemoveWork(_)) =>
      Behaviors.same
    case (_, WorkTimeout(_, _)) =>
      Behaviors.same
  }

  def behaviorWithAllocatedWork(allocated: Map[WorkId, _]): Behavior[Protocol] = Behaviors.receive {
    case (_, FetchWork(id, replyTo)) =>
      allocated.get(id).foreach(a => replyTo ! PendingWorkStorage.Retry(id, a))
      Behaviors.same
    case (_, WorkAllocated(id, work)) =>
      behaviorWithAllocatedWork(allocated + (id -> work))
    case (ctx, RemoveWork(id)) =>
      ctx.log.info(s"Removing work ${id}")
      ctx.system.eventStream ! Publish(PendingWorkStorage.WorkCompleted(id))
      behaviorWithAllocatedWork(allocated - id)
    case (ctx, WorkTimeout(id, replyTo: ActorRef[Retry[_]])) =>
      allocated.get(id).foreach {
        work =>
          replyTo ! Retry(id, work)
          ctx.system.eventStream ! Publish(PendingWorkStorage.WorkCompletionTimedOut(id))
      }
      behaviorWithAllocatedWork(allocated - id)
  }

  sealed trait Protocol

  case class WorkAllocated[W](
                               id: WorkId,
                               work: W
                             ) extends Protocol

  case class FetchWork(id: WorkId, replyTo: ActorRef[PendingWorkStorage.Retry[_]]) extends Protocol
  case class RemoveWork(id: WorkId) extends Protocol
  case class WorkTimeout(id: WorkId, replyTo: ActorRef[PendingWorkStorage.Retry[_]]) extends Protocol
}