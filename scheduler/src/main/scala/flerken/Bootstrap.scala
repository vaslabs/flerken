package flerken

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.server.Directives._
import akka.actor.typed.{ActorSystem, PostStop}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.Http
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.stream.Materializer
import akka.util.Timeout
import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.implicits._
import flerken.http.SchedulerHttp

import scala.concurrent.duration._
object Bootstrap extends IOApp{

  val guardianBehaviour = Behaviors.setup[String] { ctx =>

    implicit val timeout = Timeout(3 seconds)
    implicit val scheduler = ctx.system.scheduler
    implicit val system = ctx.system.toClassic
    implicit val executionContext = ctx.executionContext
    val server = AkkaManagement(system).start().map {
      uri =>
        ctx.log.info("Started akka management at {}", uri)
        ClusterBootstrap(system).start()
        val sharding = ClusterSharding(ctx.system)

        val workerGroup = WorkerGroup.shardRegion(WorkerGroup.Config(1 minute, 20 seconds, 100), sharding)
        val resultStorage = ctx.spawn(ResultStorage.behavior(Map.empty), "ResultStorage")

        val api = new ActorBasedSchedulerApi(workerGroup, resultStorage) with SchedulerHttp
        api
    }.flatMap {
      api =>
        implicit val materializer = Materializer(ctx)

        val server = IO.fromFuture(IO(Http().bindAndHandle(api.route ~ api.docsRoute, "0.0.0.0", 8080))).unsafeToFuture()
        server
    }


    Behaviors.receiveSignal {
      case (_, PostStop) =>
        IO.fromFuture(IO(server)).map(_.terminate(10 seconds)).unsafeRunSync()
        Behaviors.stopped
    }
  }

  private val actorSystemResource = Resource.make(
    IO(ActorSystem(guardianBehaviour, "flerken-work-scheduler"))
  )(system => IO {
    system.terminate()
  })


  override def run(args: List[String]): IO[ExitCode] = {

    actorSystemResource.use {
      _ => IO.never
    } *> IO.pure(ExitCode.Error)
  }
}
