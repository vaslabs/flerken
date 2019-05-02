package flerken.worker.http

import akka.actor.typed.ActorSystem
import cats.data.Kleisli
import cats.effect.IO
import flerken.worker.Protocol.{Notification, NotificationAck}
import io.circe.{Decoder, Encoder}

class SyntaxSpec {

  sealed trait Work
  sealed trait Result

  implicit def decoder: Decoder[Work] = ???
  implicit def encoder: Encoder[Work] = ???
  implicit def processing: Kleisli[IO, Work, Result] = ???
  implicit def notifier: Kleisli[IO, Notification[Result], NotificationAck] = ???


  def workRoute(actorSystem: ActorSystem[_]) = new Worker[Work](actorSystem).route

}
