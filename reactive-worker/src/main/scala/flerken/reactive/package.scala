package flerken

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import cats.data.Kleisli
import cats.effect.Effect
import flerken.worker.Protocol.WorkId

import scala.reflect.ClassTag

package object reactive {

  implicit final class KleisliReactiveOps[F[_], A, B](val kleisli: Kleisli[F, A, B])(implicit
        F: Effect[F], classTag: ClassTag[B]) {
    def andThenReactive[C](nextKleisli: Kleisli[F, B, C]): Behavior[A] = {
      val behaviorA: Behavior[A] = ReactiveWorker.fromKleisli(kleisli)
      val behaviorB: Behavior[B] = ReactiveWorker.fromKleisli(nextKleisli)
      compose(behaviorA, behaviorB)
    }

    private def compose[A, B](
          behaviorA: Behavior[A],
          behaviorB: Behavior[B]): Behavior[A] =
      Behaviors.setup { ctx =>
        val actorB = ctx.spawnAnonymous(behaviorB)
        ctx.system.toUntyped.eventStream.subscribe(actorB.toUntyped, classTag.runtimeClass)
        behaviorA
      }
  }

  sealed trait WorkerResponse

  sealed trait WorkerStatus extends WorkerResponse
  case object Idle extends WorkerResponse
  case class Working(workId: WorkId)

  case object StartedAck extends WorkerResponse
  case object StoppedAck extends WorkerResponse

}
