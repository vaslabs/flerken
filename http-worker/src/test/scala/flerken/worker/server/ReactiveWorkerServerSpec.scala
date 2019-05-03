package flerken.worker.server

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import cats.data.Kleisli
import cats.effect._
import flerken.worker.Protocol._
import flerken.worker.http.Worker
import flerken.worker.http.WorkerRouteSpec.workID
import io.circe.{Decoder, Encoder}
import org.scalatest.{Assertion, BeforeAndAfterAll, Matchers, WordSpec}

import scala.annotation.tailrec
import scala.concurrent.duration._

class ReactiveWorkerServerSpec extends WordSpec
    with ScalatestRouteTest
    with BeforeAndAfterAll
    with Matchers {
  import ReactiveWorkerServerSpec._
  import ReactiveWorkerServerSpec.json_support._
  import flerken.reactive._

  val testKit = ActorTestKit()
  val httpWorker: Worker[Work] = new Worker[Work](testKit.system)

  implicit val timeout: Timeout = Timeout(2 seconds)

  val resultStoreRef: ActorRef[ResultStore.Query[Outcome]] =
    testKit.spawn(ResultStore.behavior[Outcome](EmptyWork[Outcome](workID)), "ResultStoreSpec")


  val resultStore = new ResultStore[Outcome](testKit.system, resultStoreRef)

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "submitting work" must {
    import Worker.json_support._
    import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
    import httpWorker.jsonSupport.submitWorkEncoder
    import resultStore.jsonSupport.notificationDecoder


    "notify for work received" in {
      Post("/", SubmitWork[Work](workID, WA())) ~> httpWorker.route ~> check {
        status shouldBe StatusCodes.Accepted
        responseAs[NotificationAck] shouldBe NotificationAck(workID)
      }
    }

    "eventually have a result" in {
      val header = new RawHeader("X-FLERKEN-WORK-ID", workID.id)

      def workResult =
        Get("/", SubmitWork[Work](workID, WA())).withHeaders(header) ~> resultStore.route ~> check {
          responseAs[Notification[Outcome]]
        }

      @tailrec
      def eventually(tries: Int = 5): Assertion = {
        println(s"Eventually $tries")
        workResult match {
          case EmptyWork(_) if tries > 0 =>
            eventually(tries - 1)
          case wc @ WorkCompleted(_, _) =>
            wc shouldBe WorkCompleted(workID, OA())
          case emptyWork => emptyWork shouldBe WorkCompleted(workID, OA())
        }

      }

      eventually()
    }


  }


  val kleisliWork = Kleisli[IO, Work, Outcome] {
    _ match {
      case WA() => IO.pure(OA())
      case WB() => IO.pure(OB())
    }
  }

  val workerBehavior: Behavior[SubmitWork[Work]] = kleisliWork.behavior
  val worker = testKit.spawn(workerBehavior, "SampleWorkerReactiveWorkerServerSpec")
  val resultsListener = testKit.createTestProbe[Outcome]
}

object ReactiveWorkerServerSpec {
  sealed trait Work
  case class WA() extends Work
  case class WB() extends Work

  sealed trait Outcome
  case class OA() extends Outcome
  case class OB() extends Outcome

  object json_support {
      import io.circe.generic.semiauto._
      implicit val workDecoder: Decoder[Work] = deriveDecoder[Work]
      implicit val workEncoder: Encoder[Work] = deriveEncoder[Work]

      implicit val outcomeEncoder: Encoder[Outcome] = deriveEncoder[Outcome]
      implicit val outcomeDecoder: Decoder[Outcome] = deriveDecoder[Outcome]
  }
}
