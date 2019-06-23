package flerken

import java.util.UUID

import akka.actor.typed.eventstream.Publish
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import cats.data.NonEmptyList
import flerken.PendingWorkStorage.Retry

object PendingWorkStorage {

  def behavior(storageConfig: StorageConfig): Behavior[Protocol] = Behaviors.setup[Protocol] { ctx =>
    val allocatedWorkStorage =
      ctx.spawn(AllocatedWorkStorage.behavior, "AllocatedWorkStorage")
    behaviorAfterSetup(allocatedWorkStorage, storageConfig)
  }

  private def behaviorAfterSetup(
            allocatedWorkStorage: ActorRef[AllocatedWorkStorage.Protocol],
            storageConfig: StorageConfig): Behavior[Protocol] =
    Behaviors.receive[Protocol] {
      case (_, FetchWork(replyTo)) =>
        replyTo ! NoWork
        Behaviors.same
      case (ctx, AddWork(work, replyTo)) =>
        val identifier = UUID.randomUUID()
        replyTo ! WorkReceived(identifier)
        ctx.scheduleOnce(storageConfig.staleTimeout, ctx.self, ExpireWork(identifier))

        behaviorWithWork(
          NonEmptyList.of(PendingWork(identifier, work)),
          allocatedWorkStorage,
          storageConfig)
      case (ctx, WorkFailed(uuid)) =>
        allocatedWorkStorage ! AllocatedWorkStorage.FetchWork(uuid, ctx.self)
        Behaviors.same
      case (ctx, Retry(id, work)) =>
        ctx.system.eventStream ! Publish(WorkRetry(id))
        behaviorWithWork(
          NonEmptyList.of(PendingWork(id, work)),
          allocatedWorkStorage,
          storageConfig)
      case (_, CompleteWork(identifier)) =>
        allocatedWorkStorage ! AllocatedWorkStorage.RemoveWork(identifier)
        Behaviors.same
      case (_, ExpireWork(_)) =>
        Behaviors.same
      case (ctx, WorkAllocationTimeout(id)) =>
        allocatedWorkStorage ! AllocatedWorkStorage.WorkTimeout(id, ctx.self)
        Behaviors.same
  }

  private def behaviorWithWork(
                  pendingWork: NonEmptyList[PendingWork[_]],
                  allocatedWorkStorage: ActorRef[AllocatedWorkStorage.Protocol],
                  storageConfig: StorageConfig): Behavior[Protocol] =
    Behaviors.receive[Protocol] {
      case (_, FetchWork(_)) => Behaviors.unhandled
      case (ctx, AddWork(work, replyTo)) =>
        val identifier = UUID.randomUUID()
        replyTo ! WorkReceived(identifier)

        ctx.scheduleOnce(
          storageConfig.staleTimeout,
          ctx.self,
          ExpireWork(identifier)
        )

        val newWorkQueue = pendingWork :+ PendingWork(identifier, work)
        if (newWorkQueue.size >= storageConfig.highWatermark) {
          ctx.system.eventStream ! Publish(HighWatermarkReached(storageConfig.identifier))
          behaviorWithHighWatermark(newWorkQueue, allocatedWorkStorage, storageConfig)
        }
        else
          behaviorWithWork(
            newWorkQueue,
            allocatedWorkStorage,
            storageConfig
          )
      case (ctx, Retry(id, work)) =>
        ctx.system.eventStream ! Publish(WorkRetry(id))
        behaviorWithWork(pendingWork :+ PendingWork(id, work), allocatedWorkStorage, storageConfig)
      case (_, CompleteWork(_)) => Behaviors.unhandled
      case (_, WorkFailed(_)) => Behaviors.unhandled
      case (_, ExpireWork(_)) => Behaviors.unhandled
      case (_, WorkAllocationTimeout(_)) => Behaviors.unhandled
    } orElse fetchWorkBehaviour(pendingWork, allocatedWorkStorage, storageConfig)

  private def behaviorWithHighWatermark(
              workQueue: NonEmptyList[PendingWorkStorage.PendingWork[_]],
              allocatedWorkStorage: ActorRef[AllocatedWorkStorage.Protocol],
              config: StorageConfig): Behavior[Protocol] = Behaviors.receive[Protocol] {
    case (_, AddWork(_, replyTo)) =>
      replyTo ! WorkRejected
      Behaviors.same
    case (ctx, Retry(id, work)) =>
      ctx.system.eventStream ! Publish(WorkRetry(id))
      behaviorWithHighWatermark(workQueue :+ PendingWork(id, work), allocatedWorkStorage, config)
    case (_, ExpireWork(_)) =>
      Behaviors.unhandled
    case (_, FetchWork(_)) => Behaviors.unhandled
    case (_, CompleteWork(_)) => Behaviors.unhandled
    case (_, WorkFailed(_)) => Behaviors.unhandled
    case (_, WorkAllocationTimeout(_)) => Behaviors.unhandled
  } orElse fetchWorkBehaviour(workQueue, allocatedWorkStorage, config)

  private def fetchWorkBehaviour(
                                  pendingWork: NonEmptyList[PendingWork[_]],
                                  allocatedWorkStorage: ActorRef[AllocatedWorkStorage.Protocol],
                                  storageConfig: StorageConfig) = {
    Behaviors.receivePartial[Protocol] {
      case (ctx, FetchWork(replyTo)) =>
        val pendingWorkHead = pendingWork.head
        replyTo ! DoWork(pendingWorkHead.uuid, pendingWorkHead.work)
        ctx.scheduleOnce(
          storageConfig.workCompletionTimeout,
          ctx.self,
          WorkAllocationTimeout(pendingWorkHead.uuid)
        )
        allocatedWorkStorage ! AllocatedWorkStorage.WorkAllocated(
          pendingWorkHead.uuid,
          pendingWorkHead.work
        )
        NonEmptyList.fromList(pendingWork.tail)
          .map(remainingWork =>
            if (remainingWork.size >= storageConfig.highWatermark)
              behaviorWithHighWatermark(remainingWork, allocatedWorkStorage, storageConfig)
            else
              behaviorWithWork(remainingWork, allocatedWorkStorage, storageConfig)
          )
          .getOrElse(behaviorAfterSetup(allocatedWorkStorage, storageConfig))
      case (ctx, ExpireWork(id)) =>
        val remainingWork = pendingWork.filterNot(_.uuid == id)
        if (remainingWork.size < pendingWork.size)
          ctx.system.eventStream ! Publish(PendingWorkExpired(id))
        NonEmptyList.fromList(remainingWork)
          .map(remainingWork =>
            if (remainingWork.size >= storageConfig.highWatermark)
              behaviorWithHighWatermark(remainingWork, allocatedWorkStorage, storageConfig)
            else
              behaviorWithWork(remainingWork, allocatedWorkStorage, storageConfig))
          .getOrElse(behaviorAfterSetup(allocatedWorkStorage, storageConfig))
    }
  } orElse manageAllocatedWorkStorageBehavior(allocatedWorkStorage)

  private def manageAllocatedWorkStorageBehavior(
                    allocatedWorkStorage: ActorRef[AllocatedWorkStorage.Protocol]) = Behaviors.receivePartial[Protocol] {
    case (_, CompleteWork(identifier)) =>
      allocatedWorkStorage ! AllocatedWorkStorage.RemoveWork(identifier)
      Behaviors.same
    case (ctx, WorkFailed(identifier)) =>
      allocatedWorkStorage ! AllocatedWorkStorage.FetchWork(identifier, ctx.self)
      Behaviors.same
    case (ctx, WorkAllocationTimeout(id)) =>
      allocatedWorkStorage ! AllocatedWorkStorage.WorkTimeout(id, ctx.self)
      Behaviors.same
  }

  private case class PendingWork[W](uuid: UUID, work: W)

  sealed trait Protocol

  case class FetchWork(replyTo: ActorRef[Work]) extends Protocol

  case class AddWork[W](work: W, replyTo: ActorRef[WorkAck]) extends Protocol

  case class WorkFailed(identifier: UUID) extends Protocol
  case class CompleteWork(identifier: UUID) extends Protocol

  private[flerken] case class Retry[W](id: UUID, work: W) extends Protocol
  private case class ExpireWork(uuid: UUID) extends Protocol
  private case class WorkAllocationTimeout(uuid: UUID) extends Protocol

  sealed trait Work

  case object NoWork extends Work

  case class DoWork[W](id: UUID, work: W) extends Work

  sealed trait WorkAck
  case class WorkReceived(identifier: UUID) extends WorkAck
  case object WorkRejected extends WorkAck

  sealed trait Event

  case class WorkRetry(id: UUID) extends Event
  case class WorkCompleted(id: UUID) extends Event
  case class PendingWorkExpired(id: UUID) extends Event
  case class WorkCompletionTimedOut(id: UUID) extends Event
  case class HighWatermarkReached(identifier: String) extends Event

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

  def behaviorWithAllocatedWork(allocated: Map[UUID, _]): Behavior[Protocol] = Behaviors.receive {
    case (_, FetchWork(id, replyTo)) =>
      allocated.get(id).foreach(a => replyTo ! PendingWorkStorage.Retry(id, a))
      Behaviors.same
    case (_, WorkAllocated(id, work)) =>
      behaviorWithAllocatedWork(allocated + (id -> work))
    case (ctx, RemoveWork(id)) =>
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
                               id: UUID,
                               work: W
                             ) extends Protocol

  case class FetchWork(id: UUID, replyTo: ActorRef[PendingWorkStorage.Retry[_]]) extends Protocol
  case class RemoveWork(id: UUID) extends Protocol
  case class WorkTimeout(id: UUID, replyTo: ActorRef[PendingWorkStorage.Retry[_]]) extends Protocol
}