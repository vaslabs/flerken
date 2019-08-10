import sbt._

object Dependencies {

  object Versions {
    val catsEffect = "1.3.1"

    object Testing {
      val scalacheckShapeless = "1.2.3"

      val scalatest = "3.0.5"
      val scalacheck = "1.14.0"
    }
    object Akka {
      val management = "1.0.1"

      val http = "10.1.9"
      val circeHttp = "1.25.2"
      val main = "2.6.0-M5"
    }

    val twitterChill = "0.9.3"

    object Circe {
      val core = "0.11.1"
    }

    object Tapir {
      val core = "0.8.9"
    }

  }

  object Libraries {
    object Testing {
      val scalatest = "org.scalatest" %% "scalatest" % Versions.Testing.scalatest
      val scalacheck = "org.scalacheck" %% "scalacheck" % Versions.Testing.scalacheck
      val scalacheckShapless =
        "com.github.alexarchambault" %%
        "scalacheck-shapeless_1.14" %
          Versions.Testing.scalacheckShapeless
    }
    object Cats {
      val effect = "org.typelevel" %% "cats-effect" % Versions.catsEffect
    }

    object Tapir {
      val akka = Seq(
        "com.softwaremill.tapir" %% "tapir-core",
        "com.softwaremill.tapir" %% "tapir-akka-http-server",
        "com.softwaremill.tapir" %% "tapir-json-circe",
        "com.softwaremill.tapir" %% "tapir-sttp-client"
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
        "com.typesafe.akka" %% "akka-cluster-sharding-typed",
      ).map(_ % Versions.Akka.main)

      val clusterEssentials = Seq(
        "com.typesafe.akka" %% "akka-discovery" % Versions.Akka.main,
        "com.lightbend.akka.management" %% "akka-management" % Versions.Akka.management,
        "com.lightbend.akka.management" %% "akka-management-cluster-http" % Versions.Akka.management,
        "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % Versions.Akka.management
      )
      object twitterChill {
        val chill = "com.twitter" %% "chill" % Versions.twitterChill
        val chillAkka = "com.twitter" %% "chill-akka" % Versions.twitterChill
        val needed = Seq(chill, chillAkka)
      }
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
        Circe.all ++ Seq(Cats.effect, Testing.scalatest % Test, Testing.scalacheck % Test) ++
        Akka.clusterEssentials ++ Akka.twitterChill.needed
    }

    object SchedulerIntegrationTests {
      val dependencies = Tapir.akka ++
        Circe.all ++ Seq(Cats.effect, Testing.scalatest, Testing.scalacheck, Testing.scalacheckShapless % Test)
    }
  }
}
