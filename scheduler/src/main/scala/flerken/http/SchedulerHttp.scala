package flerken.http


import java.util.UUID

import akka.http.scaladsl.server.Directives._
import flerken.SchedulerApi
import flerken.protocol.Protocol._
import io.circe.Json
import tapir.Codec.PlainCodec
import tapir.model.StatusCodes
import tapir.docs.openapi._
import tapir.openapi.circe.yaml._
import tapir.swagger.akkahttp.SwaggerAkka

trait SchedulerHttp { schedulerApi: SchedulerApi =>
  import tapir.server.akkahttp._


  val route =
    SchedulerEndpoints.fetchWorkEndpoint.toRoute {
      workerId =>
        schedulerApi.fetchWork(workerId)
    } ~ SchedulerEndpoints.postWorkEndpoint.toRoute {
      work =>
        schedulerApi.storeWork(work)
    } ~ SchedulerEndpoints.workResultEndpoint.toRoute {
      workId =>
        schedulerApi.fetchWorkResult(workId)
    } ~ SchedulerEndpoints.acceptResultEndpoint.toRoute {
      storeWorkResult =>
        schedulerApi.storeWorkResult(storeWorkResult)
    }

  val docsRoute = new SwaggerAkka(
    SchedulerEndpoints.all.toOpenAPI("Flerken: The http work scheduler", "1.0").toYaml
  ).routes

}

object SchedulerEndpoints {
  import io.circe.generic.auto._
  import json_support._
  import tapir._
  import tapir.json.circe._

  implicit val workIDCodec: PlainCodec[WorkId] = Codec.uuidPlainCodec.map(WorkId)(_.value)
  implicit val workerIDCodec: PlainCodec[WorkerId] = Codec.stringPlainCodecUtf8.map(WorkerId)(_.id)
  implicit val noWorkCodec: PlainCodec[NoWork.type] = Codec.stringPlainCodecUtf8.map(_ => NoWork)(_ => "")

  private object Examples {
    val workId = WorkId(UUID.randomUUID())
    val workBody = Json.obj(
      "computeThis"-> Json.fromString("t709257423905782375490")
    )
    val workerId = WorkerId("documentation-worker")

    val someWork = SomeWork(workId, workBody)
    val storeWork = StoreWork(workerId, workBody)
    val workResultBody = Json.obj("lkgjfksl" -> Json.fromString("ghjskhjkfs"))

    val workResult = WorkResult.completed(workId, workResultBody)

    val storeWorkResult = StoreWorkResult(workId, workResultBody)
  }

  val fetchWorkEndpoint: Endpoint[WorkerId, Unit, Work, Nothing] =
    endpoint
      .in("work" / path[WorkerId].description("The worker id that owns any pending work")
        .example(Examples.workerId))
      .name("Fetch work")
      .get
      .errorOut(
        statusCode(StatusCodes.NotFound)
      )
      .out(oneOf[Work](
        statusMapping(StatusCodes.Ok, jsonBody[SomeWork].example(Examples.someWork)),
        statusMapping[NoWork.type](
          StatusCodes.NoContent, plainBody[NoWork.type].example(NoWork)
        )
      ))

  val postWorkEndpoint: Endpoint[StoreWork, Unit, WorkId, Nothing] =
    endpoint.in("work")
    .in(jsonBody[StoreWork].example(Examples.storeWork))
    .description("Submit work to be stored for a particular worker")
    .post
    .errorOut(statusCode(StatusCodes.TooManyRequests))
    .out(statusCode(StatusCodes.Created))
    .out(jsonBody[WorkId].example(Examples.workId))


  val workResultEndpoint: Endpoint[WorkId, Unit, WorkResult, Nothing] =
    endpoint.in("result" / path[WorkId].example(Examples.workId)
      .description("The id of a work that was submitted previously to view its status"))
    .get
    .description("Get the result of a previously submitted work")
    .errorOut(statusCode(StatusCodes.NotFound))
    .out(oneOf(statusMapping[WorkResult](StatusCodes.Ok, jsonBody[WorkResult].example(Examples.workResult))))

  val acceptResultEndpoint: Endpoint[StoreWorkResult, ResultRejected, Unit, Nothing] =
    endpoint.put.in("work")
    .description("Set the result of a given work")
    .in(jsonBody[StoreWorkResult].example(Examples.storeWorkResult))
    .errorOut(
      oneOf(
        statusMapping(StatusCodes.NotFound, jsonBody[ResultRejected]),
        statusMapping(StatusCodes.AlreadyReported, jsonBody[ResultRejected])
      )
    )
    .out(statusCode(StatusCodes.Accepted))

  val all = Seq(fetchWorkEndpoint, postWorkEndpoint, workResultEndpoint, acceptResultEndpoint)

}
