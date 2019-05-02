package flerken.worker

object Protocol {

  sealed trait WorkerApi

  case class ScheduledWork[F[_], Work, Result](workId: WorkId, work: Work, executor: F[Result])

  sealed trait Command extends WorkerApi
  case class SubmitWork[Work](workId: WorkId, work: Work) extends Command


  case class WorkId(id: String) extends AnyVal

  sealed trait Notification[Result] extends WorkerApi {
    def workId: WorkId
  }
  case class PendingWork[Result](workId: WorkId) extends Notification[Result]

  sealed trait WorkFinished[Work] extends Notification[Work]
  case class WorkCompleted[Result](workId: WorkId, result: Result) extends WorkFinished[Result]
  case class WorkError[Result](workId: WorkId) extends WorkFinished[Result]

  case class NotificationAck(workId: WorkId) extends WorkerApi

  case class EmptyWork[Result](workId: WorkId) extends Notification[Result]
}