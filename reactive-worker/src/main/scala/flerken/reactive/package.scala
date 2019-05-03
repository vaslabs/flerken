package flerken

import akka.actor.typed.Behavior
import cats.data.Kleisli
import cats.effect.Effect
import flerken.worker.Protocol.{SubmitWork, WorkId}

import scala.reflect.ClassTag

package object reactive {

  implicit final class KleisliReactiveOps[F[_], A, B](val kleisli: Kleisli[F, A, B])(implicit
        F: Effect[F],
        classTagA: ClassTag[A],
        classTagB: ClassTag[B]) {
    def andThenReactive[C](nextKleisli: Kleisli[F, B, C]): Behavior[SubmitWork[A]] = {
      val behaviorB: Behavior[SubmitWork[B]] = ReactiveWorker.fromKleisli(nextKleisli)
      ReactiveWorker.fromKleisli(kleisli, Some(behaviorB))
    }

    def behavior: Behavior[SubmitWork[A]] = ReactiveWorker.fromKleisli(kleisli)


  }

  sealed trait WorkerResponse

  sealed trait WorkerStatus extends WorkerResponse
  case object Idle extends WorkerResponse
  case class Working(workId: WorkId)

  case object StartedAck extends WorkerResponse
  case object StoppedAck extends WorkerResponse

}
