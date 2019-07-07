package flerken.http

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import flerken.protocol.Protocol._
import tapir.Codec.PlainCodec
import tapir.model.StatusCodes

import scala.concurrent.Future

class SchedulerHttp() {
  import tapir.server.akkahttp._


  val route =
    SchedulerEndpoints.fetchWorkEndpoint.toRoute {
      _ =>
        Future.successful(Left(()))
    } ~ SchedulerEndpoints.postWorkEndpoint.toRoute {
      _ =>
        Future.successful(Right(WorkId(UUID.randomUUID)))
    } ~ SchedulerEndpoints.workResultEndpoint.toRoute {
      workId =>
        Future.successful(Right(WorkResult.pending(workId)))
    }


}

object SchedulerEndpoints {
  import json_support._
  import tapir._
  import tapir.json.circe._

  implicit val workIDCodec: PlainCodec[WorkId] = Codec.uuidPlainCodec.map(WorkId)(_.value)
  implicit val workerIDCodec: PlainCodec[WorkerId] = Codec.stringPlainCodecUtf8.map(WorkerId)(_.id)

  val fetchWorkEndpoint: Endpoint[WorkerId, Unit, Work, Nothing] =
    endpoint
      .in("work" / path[WorkerId].description("The worker id that owns any pending work"))
      .name("Fetch work")
      .get
      .errorOut(
        statusCode(StatusCodes.NoContent)
      )
      .out(jsonBody[Work])

  val postWorkEndpoint: Endpoint[StoreWork, Unit, WorkId, Nothing] =
    endpoint.in("work")
    .in(jsonBody[StoreWork])
    .description("Submit work to be stored for a particular worker")
    .post
    .errorOut(statusCode(StatusCodes.TooManyRequests))
    .out(statusCode(StatusCodes.Created))
    .out(jsonBody[WorkId])


  val workResultEndpoint: Endpoint[WorkId, Unit, WorkResult, Nothing] =
    endpoint.in("result" / path[WorkId]
      .description("The id of a work that was submitted previously to view its status"))
    .get
    .description("Get the result of a previously submitted work")
    .errorOut(statusCode(StatusCodes.NotFound))
    .out(oneOf(statusMapping[WorkResult](StatusCodes.Ok, jsonBody[WorkResult])))



}
