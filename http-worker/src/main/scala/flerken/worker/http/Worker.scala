package flerken.worker.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import cats.data.Kleisli
import cats.effect._
import cats.effect.implicits._
import flerken.worker.Protocol.{Notification, NotificationAck}
import flerken.worker.Unique
import io.circe.Decoder


object Worker {

  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
  import io.circe.generic.auto._

  def route[F[_], Work, Result](implicit
                                decoder: Decoder[Work],
                                F: Effect[F],
                                P: Kleisli[F, Work, Result],
                                N: Kleisli[F, Notification, NotificationAck],
                                uni: Unique[F, Work]): Route = {
    val worker = new flerken.worker.Worker(N, P)
    pathSingleSlash {
      post {
        entity(as[Work]) { work =>
            worker.task.run(work).toIO.unsafeToFuture()
            complete(StatusCodes.Accepted)
        }
      }
    }
  }


}
