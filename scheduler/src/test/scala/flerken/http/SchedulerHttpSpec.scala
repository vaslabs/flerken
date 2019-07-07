package flerken.http

import java.util.concurrent.atomic.AtomicReference

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import flerken.AkkaBase
import io.circe.Json
import org.scalacheck.Gen
import org.scalatest.{Matchers, WordSpec}
import json_support._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import flerken.protocol.Protocol.{StoreWork, WorkId, WorkResult, WorkerId}

class SchedulerHttpSpec extends WordSpec with ScalatestRouteTest with AkkaBase with Matchers{

  val workerId = WorkerId(Gen.alphaNumStr.sample.get)

  "scheduler" must {

    val workIdRef = new AtomicReference[WorkId]()

    val schedulerHttp = new SchedulerHttp()

    "give empty work" in {
      Get(s"/work/${workerId.id}") ~> schedulerHttp.route ~> check {
        response.status shouldBe StatusCodes.NoContent
      }
    }

    "store work" in {
      Post(s"/work", StoreWork(workerId, Json.obj("num" -> Json.fromInt(2)))) ~> schedulerHttp.route ~> check {
        val workId = responseAs[WorkId]
        workIdRef.set(workId)
        response.status shouldBe StatusCodes.Created
      }
    }

    "query work status" in {
      Get(s"/result/${workIdRef.get().value}") ~> schedulerHttp.route ~> check {
        responseAs[WorkResult] shouldBe WorkResult.pending(workIdRef.get())
      }
    }

  }
}
