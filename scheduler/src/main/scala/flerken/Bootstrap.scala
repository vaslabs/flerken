package flerken

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import cats.effect.{ExitCode, IO, IOApp, Resource}
object Bootstrap extends IOApp{

  val guardianBehaviour = Behaviors.setup[String] { _ =>

    Behaviors.ignore
  }

  private val actorSystemResource = Resource.make(
    IO(ActorSystem(guardianBehaviour, "WorkScheduler"))
  )(system => IO {
    system.terminate()
  })

  private def clusterSharding(actorSystem: ActorSystem[_]) =
    Resource.make(IO(ClusterSharding(actorSystem)))(_ => IO.unit)

  override def run(args: List[String]): IO[ExitCode] = {
    actorSystemResource.flatMap(clusterSharding)
        .use(_ => IO.unit)
    IO.pure(ExitCode.Error)
  }
}
