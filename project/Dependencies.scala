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
      val http = "10.1.7"
      val circeHttp = "1.25.2"
      val core = "2.5.19"
    }

    object Circe {
      val core = "0.10.0"
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

    object Akka {
      val http = Seq(
        "com.typesafe.akka" %% "akka-http" % Versions.Akka.http,
        "de.heikoseeberger" %% "akka-http-circe" % Versions.Akka.circeHttp,
        "com.typesafe.akka" %% "akka-http-testkit" % Versions.Akka.http % Test,
        "com.typesafe.akka" %% "akka-stream-testkit" % Versions.Akka.core % Test,
      )
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
    object Worker {
      val dependencies = Seq(Cats.effect, Testing.scalatest, Testing.scalacheck)
    }

    object HttpWorker {
      val dependencies = Akka.http ++ Circe.all ++ Seq(Testing.scalatest, Testing.scalacheck)
    }
  }
}
