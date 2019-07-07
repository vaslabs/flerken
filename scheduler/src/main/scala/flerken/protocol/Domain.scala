package flerken.protocol

import java.util.UUID

import io.circe.Json

object Protocol {
  case class WorkerId(id: String) extends AnyVal

  case class WorkId(value: UUID) extends AnyVal

  case class Work(workId: WorkId, payload: Json)
  case class StoreWork(workerId: WorkerId, work: Json)

  sealed trait WorkStatus
  case object Pending extends WorkStatus


  sealed trait WorkResult {
    def workId: WorkId
    def status: WorkStatus
  }

  object WorkResult {
    def pending(workId: WorkId) = UncompletedWorkResult(workId, Pending)
  }

  case class UncompletedWorkResult (workId: WorkId, status: WorkStatus) extends WorkResult
}
