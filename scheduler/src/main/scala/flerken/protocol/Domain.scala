package flerken.protocol

import java.util.UUID

import akka.actor.typed.ActorRef
import flerken.PendingWorkStorage
import io.circe.Json

object Protocol {
  case class WorkerId(id: String) extends AnyVal

  case class WorkId(value: UUID) extends AnyVal

  sealed trait Work
  case class SomeWork(workId: WorkId, payload: Json) extends Work
  case object NoWork extends Work

  case class StoreWork(workerId: WorkerId, work: Json)

  case class StoreWorkResult(workId: WorkId, result: Json)

  sealed trait WorkStatus
  case object Pending extends WorkStatus
  case object Completed extends WorkStatus


  sealed trait InternalWorkResult {
    def toExternal: WorkResult
  }

  sealed trait WorkResult {
    def workId: WorkId
    def status: WorkStatus
  }

  object WorkResult {
    def completed(id: WorkId, result: Json) = CompletedWorkResult(id, Completed, result)

    def pending(workId: WorkId, replyTo: ActorRef[PendingWorkStorage.CompleteWork]) = UncompletedWorkResult(workId, Pending, replyTo)
  }

  case class UncompletedWorkResult (workId: WorkId, status: WorkStatus, replyTo: ActorRef[PendingWorkStorage.CompleteWork]) extends InternalWorkResult {
    override def toExternal: WorkResult = PendingWorkResult(workId, status)
  }

  case class CompletedWorkResult(workId: WorkId, status: WorkStatus, result: Json) extends WorkResult with InternalWorkResult {
    override def toExternal: WorkResult = this
  }
  case class PendingWorkResult(workId: WorkId, status: WorkStatus) extends WorkResult

  case class ResultRejected(reason: String)
}
