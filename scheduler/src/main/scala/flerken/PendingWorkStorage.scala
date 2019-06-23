package flerken

import java.util.UUID

import akka.actor.typed.eventstream.Publish
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import cats.data.NonEmptyList

object PendingWorkStorage {

  val behavior = Behaviors.setup[Protocol] { ctx =>
    val allocatedWorkStorage = ctx.spawn(AllocatedWorkStorage.behavior, "AllocatedWorkStorage")
    behaviorAfterSetup(allocatedWorkStorage)
  }

  private def behaviorAfterSetup(allocatedWorkStorage: ActorRef[AllocatedWorkStorage.Protocol]): Behavior[Protocol] =
    Behaviors.receive[Protocol] {
      case (_, FetchWork(replyTo)) =>
        replyTo ! NoWork
        Behaviors.same
      case (_, AddWork(work, replyTo)) =>
        val identifier = UUID.randomUUID()
        replyTo ! WorkAck(identifier)
        behaviorWithWork(NonEmptyList.of(PendingWork(identifier, work)), allocatedWorkStorage)
      case (ctx, WorkFailed(uuid)) =>
        allocatedWorkStorage ! AllocatedWorkStorage.FetchWork(uuid, ctx.self)
        Behaviors.same
      case (ctx, Retry(id, work)) =>
        ctx.system.eventStream ! Publish(WorkRetry(id))
        behaviorWithWork(NonEmptyList.of(PendingWork(id, work)), allocatedWorkStorage)
      case (_, CompleteWork(identifier)) =>
        allocatedWorkStorage ! AllocatedWorkStorage.RemoveWork(identifier)
        Behaviors.same
  }

  private def behaviorWithWork(
                  pendingWork: NonEmptyList[PendingWork[_]],
                  allocatedWorkStorage: ActorRef[AllocatedWorkStorage.Protocol]): Behavior[Protocol] =
    Behaviors.receive[Protocol] {
      case (_, FetchWork(replyTo)) =>
        val pendingWorkHead = pendingWork.head
        replyTo ! DoWork(pendingWorkHead.uuid, pendingWorkHead.work)
        allocatedWorkStorage ! AllocatedWorkStorage.WorkAllocated(
          pendingWorkHead.uuid,
          pendingWorkHead.work
        )
        NonEmptyList.fromList(pendingWork.tail).map(behaviorWithWork(_, allocatedWorkStorage))
          .getOrElse(behaviorAfterSetup(allocatedWorkStorage))
      case (_, AddWork(work, replyTo)) =>
        val identifier = UUID.randomUUID()
        replyTo ! WorkAck(identifier)
        behaviorWithWork(pendingWork :+ PendingWork(identifier, work), allocatedWorkStorage)
      case (ctx, WorkFailed(identifier)) =>
        allocatedWorkStorage ! AllocatedWorkStorage.FetchWork(identifier, ctx.self)
        Behaviors.same
      case (ctx, Retry(id, work)) =>
        ctx.system.eventStream ! Publish(WorkRetry(id))
        behaviorWithWork(pendingWork :+ PendingWork(id, work), allocatedWorkStorage)
      case (_, CompleteWork(identifier)) =>
        allocatedWorkStorage ! AllocatedWorkStorage.RemoveWork(identifier)
        Behaviors.same
    }

  private case class PendingWork[W](uuid: UUID, work: W)

  sealed trait Protocol

  case class FetchWork(replyTo: ActorRef[Work]) extends Protocol

  case class AddWork[W](work: W, replyTo: ActorRef[WorkAck]) extends Protocol

  case class WorkFailed(identifier: UUID) extends Protocol
  case class CompleteWork(identifier: UUID) extends Protocol

  private[flerken] case class Retry[W](id: UUID, work: W) extends Protocol

  sealed trait Work

  case object NoWork extends Work

  case class DoWork[W](id: UUID, work: W) extends Work

  case class WorkAck(identifier: UUID)

  sealed trait Event

  case class WorkRetry(id: UUID) extends Event
  case class WorkCompleted(id: UUID) extends Event

}

private object AllocatedWorkStorage {

  def behavior: Behavior[Protocol] = Behaviors.receiveMessage[Protocol] {
    case FetchWork(_, _) =>
      Behaviors.same
    case WorkAllocated(id, work) =>
      behaviorWithAllocatedWork(Map(id -> work))
    case RemoveWork(_) =>
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
  }

  sealed trait Protocol

  case class WorkAllocated[W](
                               id: UUID,
                               work: W
                             ) extends Protocol

  case class FetchWork(id: UUID, replyTo: ActorRef[PendingWorkStorage.Retry[_]]) extends Protocol
  case class RemoveWork(id: UUID) extends Protocol
}