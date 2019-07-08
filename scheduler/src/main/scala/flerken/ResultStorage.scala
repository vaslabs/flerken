package flerken

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import flerken.protocol.Protocol.{CompletedWorkResult, WorkId, WorkResult}
import io.circe.Json

object ResultStorage {


  def behavior(storage: Map[WorkId, WorkResult]): Behavior[Protocol] =
    Behaviors.receiveMessage {
      case FetchResult(workId, replyTo) =>
        replyTo ! storage.get(workId)
        Behaviors.same
      case StoreResult(workId, result, replyTo) =>
        storage.get(workId) match {
          case Some(CompletedWorkResult(_, _, _)) =>
            replyTo ! DuplicateResult
            Behaviors.same
          case _ =>
            replyTo ! ResultAccepted
            behavior(storage + (workId -> WorkResult.completed(workId, result)))
        }
      case WaitForResult(workId) =>
        if (storage.contains(workId))
          Behaviors.same
        else
          behavior(storage + (workId -> WorkResult.pending(workId)))
    }


  sealed trait Protocol


  case class FetchResult(workId: WorkId, ref: ActorRef[Option[WorkResult]]) extends Protocol
  case class StoreResult(workId: WorkId, result: Json, ref: ActorRef[ResultAck]) extends Protocol
  case class WaitForResult(workId: WorkId) extends Protocol

  sealed trait ResultAck
  case object ResultAccepted extends ResultAck
  sealed trait ResultRejected extends ResultAck

  case object DuplicateResult extends ResultRejected


}
