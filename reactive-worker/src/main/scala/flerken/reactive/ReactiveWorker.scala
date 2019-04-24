package flerken.reactive

import akka.actor.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import cats.data.Kleisli
import cats.effect.Effect

import scala.util.{Failure, Success}

object ReactiveWorker {

  private def publish[A](message: A)(implicit actorSystem: ActorSystem): Unit =
    actorSystem.eventStream.publish(message)

  private def kernelErrorUnsafe[F[_], A](eff: F[A])(implicit F: Effect[F]): Behavior[KernelErrorResult[A]] = Behaviors.setup {
    ctx =>
      F.toIO(eff).unsafeToFuture().onComplete {
        case Success(res) =>
          ctx.self ! Right(res)
        case Failure(err) =>
          ctx.self ! Left(err)
      }(ctx.executionContext)
      Behaviors.receive[KernelErrorResult[A]] {
        case (ctx, Right(res)) =>
          publish(res)(ctx.system.toUntyped)
          Behavior.stopped
        case (_, Left(throwable)) =>
          throw throwable
      }
  }

  private type KernelErrorResult[A] = Either[Throwable, A]


  def fromKleisli[F[_], Input, Output](
          kleisli: Kleisli[F, Input, Output])(implicit
          F: Effect[F]): Behavior[Input] =
    Behaviors.receive {
      case (ctx, input) =>
        val effect = kleisli.run(input)
        ctx.spawnAnonymous(kernelErrorUnsafe(effect))
        Behaviors.same
    }

}
