import Dependencies._
import sbt._

object Dependencies {

  object Versions {
    val catsEffect = "1.2.0"

    object Testing {
      val scalatest = "3.0.5"
      val scalacheck = "1.14.0"
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
  }

  object Modules {
    import Libraries._
    object Worker {
      val dependencies = Seq(Cats.effect, Testing.scalatest, Testing.scalacheck)
    }
  }
}
