package flerken.worker.http

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.data.Kleisli
import cats.effect.IO
import cats.effect.implicits._
import flerken.worker.Protocol.{Notification, NotificationAck, WorkId}
import flerken.worker.Unique
import io.circe.generic.auto._
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._
import scala.util.Success

class WorkerRouteSpec extends WordSpec with Matchers with ScalatestRouteTest {

  import WorkerRouteSpec._
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

  val notificationAckPromise = Promise.apply[NotificationAck]()

  implicit lazy val notifier: Kleisli[IO, Notification, NotificationAck] = Kleisli[IO, Notification, NotificationAck] {
    notification =>
      IO {
        val notificationAck = NotificationAck(notification.workId)
        notificationAckPromise.complete(Success(NotificationAck(notification.workId)))
        notificationAck
      }
  }

  "worker route" can {
    val route = Worker.route[IO, SampleWork, SampleResult]
    val work: SampleWork =  WorkStringConcatenation("a", 1, 2.0)
    "notify for work received" in {
      Post("/", work) ~> route ~> check {
        status shouldBe StatusCodes.Accepted
        Await.result(notificationAckPromise.future, 3 seconds)
      }
    }

  }

}

object WorkerRouteSpec {

  sealed trait SampleWork
  case class WorkStringConcatenation(string: String, number: Int, realNumber: Double) extends SampleWork
  case class FloatOrString(string: String, floatNumber: Float, displayString: Boolean) extends SampleWork

  sealed trait SampleResult
  case class Result(concatenatedString: String) extends SampleResult

  implicit lazy val processing: Kleisli[IO, SampleWork, SampleResult] = Kleisli[IO, SampleWork, SampleResult] {
    _ => IO.never
  }

  implicit lazy val unique: Unique[IO, SampleWork] = (_: SampleWork) =>
    IO.pure(UUID.randomUUID().toString)
}
