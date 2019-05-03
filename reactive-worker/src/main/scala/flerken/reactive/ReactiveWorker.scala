package flerken.reactive

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import cats.data.Kleisli
import cats.effect.Effect
import flerken.worker.Protocol.{SubmitWork, WorkCompleted, WorkId}

import scala.util.{Failure, Success}
import scala.concurrent.duration._

object ReactiveWorker {


  private def publish[A](message: A)(implicit actorSystem: ActorSystem[_]): Unit =
    actorSystem.toUntyped.eventStream.publish(message)

  type Forwarder[A] = Option[ActorRef[SubmitWork[A]]]

  private def kernelErrorUnsafe[F[_], A](
      workId: WorkId,
      eff: F[A],
      forwardTo: Forwarder[A])(implicit
      F: Effect[F]): Behavior[KernelErrorResult[A]] =
    Behaviors.supervise[KernelErrorResult[A]](
      Behaviors.setup {
        ctx =>
          F.toIO(eff).unsafeToFuture().onComplete {
            case Success(res) =>
              ctx.self ! Right(res)
            case Failure(err) =>
              ctx.self ! Left(err)
          }(ctx.executionContext)
          Behaviors.receive[KernelErrorResult[A]] {
            case (ctx, Right(res)) =>
              forwardTo match {
                case None => publish(WorkCompleted(workId, res))(ctx.system)
                case Some(forwarder) => forwarder ! SubmitWork(workId, res)
              }
              Behavior.stopped
            case (_, Left(throwable)) =>
              throw throwable
          }
  }).onFailure[RuntimeException](
      SupervisorStrategy.restartWithBackoff(
        1 second,
        60 seconds,
        0.2
      ).withMaxRestarts(3))

  private type KernelErrorResult[A] = Either[Throwable, A]


  def fromKleisli[F[_], Input, Output](
          kleisli: Kleisli[F, Input, Output],
          composeWith: Option[Behavior[SubmitWork[Output]]] = None)(implicit
          F: Effect[F]): Behavior[SubmitWork[Input]] = Behaviors.setup[SubmitWork[Input]] { ctx =>
    val forwardTo : Option[ActorRef[SubmitWork[Output]]] = composeWith.map {
      behavior => ctx.spawnAnonymous(behavior)
    }
    ctx.system.toUntyped.eventStream.subscribe(ctx.self.toUntyped, classOf[SubmitWork[Input]])
    Behaviors.receive {
      case (ctx, input) =>
        val effect = kleisli.run(input.work)
        ctx.spawnAnonymous(kernelErrorUnsafe[F, Output](input.workId, effect, forwardTo))
        Behaviors.same
    }
  }

}
