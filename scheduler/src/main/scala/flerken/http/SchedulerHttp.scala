package flerken.http

import java.util.UUID

import io.circe.{Decoder, Encoder, Json}
import tapir.model.StatusCodes

import scala.concurrent.Future
import akka.http.scaladsl.server.Directives._

class SchedulerHttp() {
  import tapir.server.akkahttp._


  val route =
    SchedulerEndpoints.fetchWorkEndpoint.toRoute {
      _ =>
        Future.successful(Left(()))
    } ~ SchedulerEndpoints.postWorkEndpoint.toRoute {
      _ =>
        Future.successful(Right(UUID.randomUUID()))
    }


}

object SchedulerEndpoints {

  import json_support._
  import tapir._

  import io.circe.generic.auto._
  import tapir.json.circe._


  import Protocol._
  val fetchWorkEndpoint: Endpoint[String, Unit, Work, Nothing] =
    endpoint
      .in("work" / path[String].description("The worker id that owns any pending work"))
      .name("Fetch work")
      .get
      .errorOut(
        statusCode(StatusCodes.NoContent)
      )
      .out(jsonBody[Work])

  val postWorkEndpoint: Endpoint[StoreWork, Unit, UUID, Nothing] =
    endpoint.in("work")
    .in(jsonBody[StoreWork])
    .description("Submit work to be stored for a particular worker")
    .post
    .errorOut(statusCode(StatusCodes.TooManyRequests))
    .out(statusCode(StatusCodes.Created))
    .out(jsonBody[UUID])

}

object Protocol {
  case class WorkerId(id: String) extends AnyVal

  case class Work(workId: UUID, payload: Json)
  case class StoreWork(workerId: WorkerId, work: Json)
}

object json_support {
  import Protocol._
  import io.circe.generic.semiauto._
  implicit val workEncoder: Encoder[Work] = deriveEncoder[Work]
  implicit val workDecoder: Decoder[Work] = deriveDecoder[Work]

  implicit val workerIdEncoder: Encoder[WorkerId] = Encoder.encodeString.contramap(_.id)
  implicit val workerIdDecoder: Decoder[WorkerId] = Decoder.decodeString.map(WorkerId)
}