package flerken

import akka.actor.typed.{ActorSystem, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import cats.effect.{ExitCode, IO, IOApp, Resource}
import flerken.http.SchedulerHttp

import scala.concurrent.duration._

object Bootstrap extends IOApp{

  val guardianBehaviour = Behaviors.setup[String] { ctx =>
    val sharding = ClusterSharding(ctx.system)

    val workerGroup = WorkerGroup.shardRegion(WorkerGroup.Config(1 minute, 20 seconds, 100), sharding)
    val resultStorage = ctx.spawn(ResultStorage.behavior(Map.empty), "ResultStorage")

    implicit val timeout = Timeout(3 seconds)
    implicit val scheduler = ctx.system.scheduler
    implicit val system = ctx.system.toUntyped
    val api = new ActorBasedSchedulerApi(workerGroup, resultStorage) with SchedulerHttp
    implicit val actorMaterializer = ActorMaterializer()

    val server = IO.fromFuture(IO(Http().bindAndHandle(api.route, "0.0.0.0", 8080))).unsafeToFuture()
    Behaviors.receiveSignal {
      case (_, PostStop) =>
        IO.fromFuture(IO(server)).map(_.terminate(10 seconds)).unsafeRunSync()
        Behaviors.stopped
    }
  }

  private val actorSystemResource = Resource.make(
    IO(ActorSystem(guardianBehaviour, "FlerkenWorkScheduler"))
  )(system => IO {
    system.terminate()
  })


  override def run(args: List[String]): IO[ExitCode] = {

    actorSystemResource.use {
      _ => IO.unit
    }
    IO.pure(ExitCode.Error)
  }
}
