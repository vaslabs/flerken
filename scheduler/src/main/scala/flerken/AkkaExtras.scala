package flerken

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

object AkkaExtras {

  type PartialHandling[A] = PartialFunction[A, Behavior[A]]
  type PartialHandlingWithContext[A] = PartialFunction[(ActorContext[A], A), Behavior[A]]

  type BehaviorWithContextHandler[A] = List[PartialHandlingWithContext[A]]
  type BehaviorHandler[A] = List[PartialHandling[A]]

  implicit final class AkkaExtras[A](behaviorHandlers: BehaviorHandler[A]) {
    private[this] def handle(msg: A, handlers: BehaviorHandler[A]): Behavior[A] = {
      handlers match {
        case Nil          => Behaviors.unhandled
        case head :: tail => head.applyOrElse[A, Behavior[A]](msg, _ => handle(msg, tail))
      }
    }

    def toBehavior = Behaviors.receiveMessage[A] {
      msg => handle(msg, behaviorHandlers)
    }
  }

  implicit final class AkkaExtrasCtx[A](behaviorHandlers: BehaviorWithContextHandler[A]) {
    private[this] def handle(ctx: ActorContext[A], msg: A, handlers: BehaviorWithContextHandler[A]): Behavior[A] = {
      handlers match {
        case Nil          => Behaviors.unhandled[A]
        case head :: tail => head
          .applyOrElse[(ActorContext[A], A), Behavior[A]]((ctx, msg), _ => handle(ctx, msg, tail))
      }
    }
    def toBehavior = Behaviors.receive[A] {
      case (ctx, msg) => handle(ctx, msg, behaviorHandlers)
    }


  }
}
