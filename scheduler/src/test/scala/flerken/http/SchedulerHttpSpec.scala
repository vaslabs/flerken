package flerken.http

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import flerken.AkkaBase
import flerken.http.Protocol.{StoreWork, WorkerId}
import io.circe.Json
import org.scalacheck.Gen
import org.scalatest.{Matchers, WordSpec}
import json_support._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

class SchedulerHttpSpec extends WordSpec with ScalatestRouteTest with AkkaBase with Matchers{

  val workerId = WorkerId(Gen.alphaNumStr.sample.get)

  "scheduler" must {


    val schedulerHttp = new SchedulerHttp()

    "give empty work" in {
      Get(s"/work/${workerId.id}") ~> schedulerHttp.route ~> check {
        response.status shouldBe StatusCodes.NoContent
      }
    }

    "store work" in {
      Post(s"/work", StoreWork(workerId, Json.obj("num" -> Json.fromInt(2)))) ~> schedulerHttp.route ~> check {
        responseAs[UUID]
        response.status shouldBe StatusCodes.Created
      }
    }

  }
}
