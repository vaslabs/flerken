package flerken

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import scala.annotation.tailrec

object AkkaExtras {

  type PartialHandling[A] = PartialFunction[A, Behavior[A]]
  type PartialHandlingWithContext[A] = PartialFunction[(ActorContext[A], A), Behavior[A]]

  type BehaviorWithContextHandler[A] = List[PartialHandlingWithContext[A]]
  type BehaviorHandler[A] = List[PartialHandling[A]]

  implicit final class AkkaExtras[A](behaviorHandlers: BehaviorHandler[A]) {
    @tailrec private[this] def handle(msg: A, handlers: BehaviorHandler[A]): Behavior[A] = {
      handlers match {
        case Nil          => Behaviors.unhandled[A]
        case head :: tail =>
          val next = head.applyOrElse[A, Behavior[A]](msg, _ => Behaviors.unhandled[A])
          if (Behavior.isUnhandled(next))
            handle(msg, tail)
          else
            next
      }
    }

    def toBehavior = Behaviors.receiveMessage[A] {
      msg => handle(msg, behaviorHandlers)
    }
  }

  implicit final class AkkaExtrasCtx[A](behaviorHandlers: BehaviorWithContextHandler[A]) {
    @tailrec
    private[this] def handle(ctx: ActorContext[A], msg: A, handlers: BehaviorWithContextHandler[A]): Behavior[A] = {
      handlers match {
        case Nil          => Behaviors.unhandled[A]
        case head :: tail =>
          val next = head.applyOrElse[(ActorContext[A], A), Behavior[A]]((ctx, msg), _ => Behaviors.unhandled[A])
          if (Behavior.isUnhandled(next))
            handle(ctx, msg, tail)
          else
            next
      }
    }
    def toBehavior = Behaviors.receive[A] {
      case (ctx, msg) => handle(ctx, msg, behaviorHandlers)
    }


  }
}
