package flerken.worker

object Protocol {

  sealed trait WorkerApi

  case class ScheduledWork[F[_], Work, Result](workId: WorkId, work: Work, executor: F[Result])



  case class WorkId(id: String) extends AnyVal

  sealed trait Notification extends WorkerApi {
    def workId: WorkId
  }
  case class PendingWork[Work](workId: WorkId, work: Work) extends Notification

  sealed trait WorkFinished extends Notification
  case class WorkCompleted[Result](workId: WorkId, result: Result) extends WorkFinished
  case class WorkError(workId: WorkId) extends WorkFinished

  case class NotificationAck(workId: WorkId)
}
