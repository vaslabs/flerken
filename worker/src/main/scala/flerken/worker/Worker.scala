package flerken.worker

import cats.data.Kleisli
import cats.effect.Async
import flerken.worker.Protocol._

import cats.implicits._


trait Unique[F[_], A] {
  def id: F[String]
}

class Worker[F[_], Work, Result](
          notifier: Kleisli[F, Notification, NotificationAck],
          workExecutor: Kleisli[F, Work, Result])(implicit
          F: Async[F],
          unique: Unique[F, Work]

) {

  private def newWorkId = unique.id.map(id => WorkId(id))

  private[worker] def receiveWork =
    Kleisli [F, Work, PendingWork[Work]] {
      work =>
        newWorkId.map(id => PendingWork(id, work))
    }

  private[worker] def pendingNotification = Kleisli[F, PendingWork[Work], PendingWork[Work]] {
    pendingWork => notifier.run(pendingWork).map(_ => pendingWork)
  }

  private[worker] def executeWork = Kleisli[F, PendingWork[Work], WorkFinished] {
    pendingWork =>
      workExecutor.map[WorkFinished](result => WorkCompleted(pendingWork.workId, result))
        .run(pendingWork.work) orElse F.delay(WorkError(pendingWork.workId))
  }

  private[worker] def completedNotification = Kleisli[F, WorkFinished, NotificationAck] {
    workCompleted =>
      notifier.run(workCompleted)
  }

  def task: Kleisli[F, Work, NotificationAck] =
    receiveWork andThen pendingNotification andThen executeWork andThen completedNotification

}