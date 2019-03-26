package flerken.worker

import cats.data.Kleisli
import cats.effect.Effect
import cats.implicits._
import flerken.worker.Protocol._


trait Unique[F[_], A] {
  def id(a: A): F[String]
}

class Worker[F[_], Work, Result](
          notifier: Kleisli[F, Notification, NotificationAck],
          workExecutor: Kleisli[F, Work, Result])(implicit
          F: Effect[F],
          unique: Unique[F, Work]

) {

  private def newWorkId(work: Work) = unique.id(work).map(id => WorkId(id))

  private[worker] def receiveWork =
    Kleisli [F, Work, PendingWork[Work]] {
      work =>
        newWorkId(work).map(id => PendingWork(id, work))
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