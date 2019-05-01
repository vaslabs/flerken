package flerken.worker.http

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import flerken.worker.Protocol.{NotificationAck, SubmitWork, WorkId}
import io.circe.{Decoder, Encoder}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class WorkerRouteSpec extends WordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll {

  import WorkerRouteSpec._
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

  val testKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()

  import WorkerRouteSpec.json_support._

  "worker route" can {
    val httpWorker: Worker[SampleWork] = new Worker[SampleWork](testKit.system)

    import httpWorker.jsonSupport._

    val work: SampleWork =  WorkStringConcatenation("a", 1, 2.0)
    "notify for work received" in {
      Post("/", SubmitWork(workID, work)) ~> httpWorker.route ~> check {
        status shouldBe StatusCodes.Accepted
        responseAs[NotificationAck] shouldBe NotificationAck(workID)
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

  implicit lazy val workID = WorkId(UUID.randomUUID().toString)

  object json_support {
    import io.circe.generic.semiauto._
    import io.circe.generic.auto._
    implicit val sampleWorkDecoder: Decoder[SampleWork] = deriveDecoder[SampleWork]
    implicit val sampleWorkEncoder: Encoder[SampleWork] = deriveEncoder[SampleWork]
  }
}
