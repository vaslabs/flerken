import Dependencies._
import sbt._

object Dependencies {

  object Versions {
    val catsEffect = "1.2.0"

    object Testing {
      val scalatest = "3.0.5"
      val scalacheck = "1.14.0"
    }
    object Akka {
      val http = "10.1.8"
      val circeHttp = "1.25.2"
      val main = "2.6.0-M4"
    }

    object Circe {
      val core = "0.10.0"
    }

    object Tapir {
      val core = "0.8.9"
    }

  }

  object Libraries {
    object Testing {
      val scalatest = "org.scalatest" %% "scalatest" % Versions.Testing.scalatest % Test
      val scalacheck = "org.scalacheck" %% "scalacheck" % Versions.Testing.scalacheck % Test
    }
    object Cats {
      val effect = "org.typelevel" %% "cats-effect" % Versions.catsEffect
    }

    object Tapir {
      val akka = Seq(
        "com.softwaremill.tapir" %% "tapir-core",
        "com.softwaremill.tapir" %% "tapir-akka-http-server",
        "com.softwaremill.tapir" %% "tapir-json-circe"
      ).map(_ % Versions.Tapir.core)
    }

    object Akka {
      val http = Seq(
        "com.typesafe.akka" %% "akka-http" % Versions.Akka.http,
        "de.heikoseeberger" %% "akka-http-circe" % Versions.Akka.circeHttp,
        "com.typesafe.akka" %% "akka-http-testkit" % Versions.Akka.http % Test,
        "com.typesafe.akka" %% "akka-stream-testkit" % Versions.Akka.main % Test
      )
      val actors = Seq(
        "com.typesafe.akka" %% "akka-actor-typed" % Versions.Akka.main,
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % Versions.Akka.main % Test
      )
      val sharding = Seq(
        "com.typesafe.akka" %% "akka-cluster-sharding-typed"
      ).map(_ % Versions.Akka.main)
    }

    object Circe {
      val all = Seq(
          "io.circe" %% "circe-core",
          "io.circe" %% "circe-generic",
          "io.circe" %% "circe-parser"
        ).map(_ % Versions.Circe.core)
    }
  }

  object Modules {
    import Libraries._
    object Scheduler {
      val dependencies =
        Akka.actors ++ Akka.sharding ++ Akka.http ++ Tapir.akka ++
        Circe.all ++ Seq(Cats.effect, Testing.scalatest, Testing.scalacheck)
    }

    object Storage {
      val dependencies = Akka.actors ++ Akka.sharding ++ Circe.all ++ Seq(Testing.scalatest, Testing.scalacheck)
    }
  }
}
