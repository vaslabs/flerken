package flerken.worker.http

import cats.data.Kleisli
import cats.effect.IO
import cats.effect.implicits._
import flerken.worker.Protocol.{Notification, NotificationAck}
import flerken.worker.Unique
import io.circe.Decoder

import scala.util.Random

class SyntaxSpec {

  sealed trait Work
  sealed trait Result

  implicit lazy val decoder: Decoder[Work] = ???
  implicit lazy val processing: Kleisli[IO, Work, Result] = ???
  implicit lazy val notifier: Kleisli[IO, Notification, NotificationAck] = ???
  implicit lazy val unique: Unique[IO, Work] = (work: Work) => IO.pure(Random.alphanumeric.take(32).mkString)


  def workRoute = Worker.route[IO, Work, Result]

}
