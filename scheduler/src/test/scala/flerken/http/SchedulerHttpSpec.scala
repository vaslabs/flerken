package flerken.http

import java.util.concurrent.atomic.AtomicReference

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import flerken.PendingWorkStorage.{AddWork, FetchWork}
import flerken.WorkerGroup.StoreWorkFor
import flerken._
import flerken.http.json_support._
import flerken.protocol.Protocol._
import io.circe.Json
import org.scalacheck.Gen
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Future
import scala.concurrent.duration._

class SchedulerHttpSpec extends WordSpec with ScalatestRouteTest with AkkaBase with Matchers{

  val workerId = WorkerId(Gen.alphaNumStr.sample.get)

  "scheduler" must {

    implicit val scheduler = testKit.system.scheduler
    implicit val timeout: Timeout = Timeout(3 seconds)

    val workGroup = testKit.createTestProbe[WorkerGroup.Protocol]
    val resultStorage = testKit.spawn(ResultStorage.behavior(Map.empty))
    val workStorage = testKit.spawn(
      PendingWorkStorage.behavior(StorageConfig(1 minute, 10 seconds, 20, workerId), resultStorage)
    )

    val workIdRef = new AtomicReference[WorkId]()

    val schedulerHttp = new ActorBasedSchedulerApi(workGroup.ref, resultStorage) with SchedulerHttp()

    "give empty work" in {
      Future {
        val query = workGroup.expectMessageType[WorkerGroup.AssignWorkTo]
        workStorage ! FetchWork(query.replyTo)
      }
      Get(s"/work/${workerId.id}") ~> schedulerHttp.route ~> check {

        response.status shouldBe StatusCodes.NoContent
      }
    }

    "store work" in {
      Future {
        val expectedCommand = workGroup.expectMessageType[StoreWorkFor[Json]]
        workStorage ! AddWork(expectedCommand.work, expectedCommand.replyTo)
      }
      Post(s"/work", StoreWork(workerId, Json.obj("num" -> Json.fromInt(2)))) ~> schedulerHttp.route ~> check {
        val workId = responseAs[WorkId]
        workIdRef.set(workId)
        response.status shouldBe StatusCodes.Created
      }
    }

    "query work status" in {
      Get(s"/result/${workIdRef.get().value}") ~> schedulerHttp.route ~> check {
        responseAs[WorkResult] shouldBe PendingWorkResult(workIdRef.get(), Pending)
      }
    }

    "complete work" in {

      Put("/work", StoreWorkResult(workIdRef.get(), Json.obj("outcome" -> Json.fromInt(4)))) ~> schedulerHttp.route ~> check {
        response.status shouldBe StatusCodes.Accepted
      }
    }


    "report result" in {
      Get(s"/result/${workIdRef.get().value}") ~> schedulerHttp.route ~> check {
        responseAs[WorkResult] shouldBe
          WorkResult.completed(workIdRef.get(),
            Json.obj("outcome" -> Json.fromInt(4))
          )
      }
    }

  }
}
