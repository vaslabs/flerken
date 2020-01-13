import sbt._

object Dependencies {

  object Versions {
    val catsEffect = "2.0.0"

    object Testing {
      val scalacheckShapeless = "1.2.3"

      val scalatest = "3.0.8"
      val scalacheck = "1.14.0"
    }
    object Akka {
      val management = "1.0.5"

      val http = "10.1.10"
      val circeHttp = "1.29.1"
      val main = "2.6.0"

    }

    object AutoDowning {
      val main = "0.0.13"
    }

    val twitterChill = "0.9.4"

    object Circe {
      val core = "0.12.2"
    }

    object Tapir {
      val core = "0.12.4"
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
        "com.softwaremill.sttp.tapir" %% "tapir-core",
        "com.softwaremill.sttp.tapir" %% "tapir-akka-http-server",
        "com.softwaremill.sttp.tapir" %% "tapir-json-circe",
        "com.softwaremill.sttp.tapir" %% "tapir-sttp-client"
      ).map(_ % Versions.Tapir.core)

      val openApi = Seq(
        "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs",
        "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml",
        "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-akka-http"
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

      val autodowning = "com.github.TanUkkii007" %% "akka-cluster-custom-downing" % Versions.AutoDowning.main

      val clusterEssentials = Seq(
        "com.typesafe.akka" %% "akka-discovery" % Versions.Akka.main,
        "com.lightbend.akka.management" %% "akka-management" % Versions.Akka.management,
        "com.lightbend.akka.management" %% "akka-management-cluster-http" % Versions.Akka.management,
        "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % Versions.Akka.management,
        autodowning
      )

      val logging = Seq(
        "com.typesafe.akka" %% "akka-slf4j" % Versions.Akka.main,
        "ch.qos.logback" % "logback-classic" % "1.2.3"
      )

      val kubernetesDiscovery = "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % Versions.Akka.management

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
        Akka.actors ++ Akka.sharding ++ Akka.http ++ Tapir.akka ++ Tapir.openApi ++ Akka.logging ++
        Circe.all ++ Seq(Cats.effect, Testing.scalatest % Test, Testing.scalacheck % Test) ++
        Akka.clusterEssentials ++ Akka.twitterChill.needed ++ Seq(Akka.kubernetesDiscovery)
    }

    object SchedulerIntegrationTests {
      val dependencies = Tapir.akka ++ Tapir.openApi ++
        Circe.all ++ Seq(Cats.effect, Testing.scalatest, Testing.scalacheck, Testing.scalacheckShapless % Test)
    }
  }
}