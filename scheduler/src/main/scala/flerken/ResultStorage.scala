package flerken

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import flerken.protocol.Protocol.{CompletedWorkResult, InternalWorkResult, UncompletedWorkResult, WorkId, WorkResult}
import io.circe.Json

object ResultStorage {


  def behavior(storage: Map[WorkId, InternalWorkResult]): Behavior[Protocol] =
    Behaviors.receive {
      case (_, FetchResult(workId, replyTo)) =>
        replyTo ! storage.get(workId).map(_.toExternal)
        Behaviors.same
      case (ctx, StoreResult(workId, result, replyTo)) =>
        ctx.log.info(s"Received store result $result, from $replyTo")
        storage.get(workId) match {
          case Some(CompletedWorkResult(_, _, _)) =>
            replyTo ! DuplicateResult
            Behaviors.same
          case Some(UncompletedWorkResult(workId, _, ref)) =>
            replyTo ! ResultAccepted
            ctx.log.info(s"Sending complete work message to $ref")
            ref ! PendingWorkStorage.CompleteWork(workId)
            behavior(storage + (workId -> WorkResult.completed(workId, result)))
          case None =>
            replyTo ! NotReady
            Behaviors.same
        }
      case (_, WaitForResult(workId, replyTo)) =>
        if (storage.filterKeys(_ == workId).nonEmpty)
          Behaviors.same
        else
          behavior(storage + (workId -> WorkResult.pending(workId, replyTo)))
    }


  sealed trait Protocol


  case class FetchResult(workId: WorkId, ref: ActorRef[Option[WorkResult]]) extends Protocol
  case class StoreResult(workId: WorkId, result: Json, ref: ActorRef[ResultAck]) extends Protocol
  case class WaitForResult(workId: WorkId, replyTo: ActorRef[PendingWorkStorage.CompleteWork]) extends Protocol

  sealed trait ResultAck
  case object ResultAccepted extends ResultAck
  sealed trait ResultRejected extends ResultAck

  case object NotReady extends ResultRejected
  case object DuplicateResult extends ResultRejected


}
