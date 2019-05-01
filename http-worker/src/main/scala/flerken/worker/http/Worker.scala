package flerken.worker.http

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import flerken.worker.Protocol.{NotificationAck, SubmitWork, WorkId}
import io.circe.{Decoder, Encoder}


class Worker[Work](actorSystem: ActorSystem[_])(implicit
                   decoder: Decoder[Work], encoder: Encoder[Work]) {

  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

  val jsonSupport = new Worker.JsonSupport[Work]
  import jsonSupport._

  lazy val route: Route = {
    pathSingleSlash {
      post {
        entity(as[SubmitWork[Work]]) { work =>
            actorSystem.toUntyped.eventStream.publish(work)
            complete(StatusCodes.Accepted -> work.workId)
        }
      }
    }
  }

}

object Worker {
  object json_support {
  }
  class JsonSupport[Work]()(implicit val decoder: Decoder[Work], val encoder: Encoder[Work]) {
    import io.circe.generic.semiauto._

    implicit val workIdDecoder = Decoder.decodeString.map(WorkId(_))
    implicit val workIdEncoder: Encoder[WorkId] = Encoder.encodeString.contramap(_.id)

    implicit private[http] val submitWorkDecoder: Decoder[SubmitWork[Work]] =
      deriveDecoder[SubmitWork[Work]]

    implicit val submitWorkEncoder: Encoder[SubmitWork[Work]] =
      deriveEncoder[SubmitWork[Work]]

    implicit private[http] val notificationAckEncoder: Encoder[NotificationAck] =
      Encoder.encodeString.contramap(_.workId.id)

    implicit val notificationAckDecoder: Decoder[NotificationAck] =
      Decoder.decodeString.map(id => NotificationAck(WorkId(id)))
  }
}
