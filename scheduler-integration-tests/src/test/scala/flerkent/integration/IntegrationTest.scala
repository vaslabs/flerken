package flerkent.integration

import sttp.client._
import flerken.http.IntegrationBase._
import flerken.http.SchedulerEndpoints
import flerken.protocol.Protocol._
import io.circe.Json
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.{Matchers, WordSpec}

class IntegrationTest extends WordSpec with Matchers{


  import DataGenerator._

  val uri =  uri"http://scheduler-node-a:8080"

  "scheduler" must {

    "tell workers there is no work" in {

      implicit val arbitraryWorkerId = arbitraryWorker

      assertion[WorkerId, Work, Unit](SchedulerEndpoints.fetchWorkEndpoint, Right(NoWork))(
        uri
      )
    }
    "send some work to a worker, when such becomes available" in {

      val (storeWork, workId) = chain(SchedulerEndpoints.postWorkEndpoint)(uri)
      workId should matchPattern {
        case Right(WorkId(_)) =>
      }
      implicit val arbitraryWorker = Arbitrary(Gen.const(storeWork.workerId))
      assertion(SchedulerEndpoints.fetchWorkEndpoint, Right(SomeWork(workId.right.get, storeWork.work)))(uri)
    }

    "store the outcome of the work" in {
      val (postedResult, _) = chainedAssertion(SchedulerEndpoints.acceptResultEndpoint, Right(()))(uri)
      implicit val arbitraryWorkId = Arbitrary(Gen.const(postedResult.workId))
      assertion(
        SchedulerEndpoints.workResultEndpoint,
        Right(CompletedWorkResult(postedResult.workId, Completed, postedResult.result))
      )(uri)
    }
  }

}

object DataGenerator {

  val arbitraryWorker = Arbitrary(Gen.alphaNumStr.map(WorkerId))

  val arbitraryJson: Arbitrary[Json] = Arbitrary(Gen.const(Json.obj(
    Gen.alphaNumStr.sample.get -> Json.fromString(Gen.alphaNumStr.sample.get)
  )))

  implicit lazy val arbitraryWork: Arbitrary[StoreWork]  = Arbitrary(
    for {
      workerId <- arbitraryWorker.arbitrary
      json <- arbitraryJson.arbitrary
    } yield StoreWork(workerId, json)
  )

  implicit lazy val storeWorkResultArbitrary: Arbitrary[StoreWorkResult] =
    Arbitrary(
      for {
        workId <- Gen.uuid.map(WorkId)
        json <- arbitraryJson.arbitrary
      } yield StoreWorkResult(workId, json)
    )


}
