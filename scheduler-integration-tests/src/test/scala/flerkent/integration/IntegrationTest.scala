package flerkent.integration

import com.softwaremill.sttp.Uri
import flerken.http.IntegrationBase._
import flerken.http.SchedulerEndpoints
import flerken.protocol.Protocol.{NoWork, Work, WorkerId}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.{Matchers, WordSpec}

class IntegrationTest extends WordSpec with Matchers{

  implicit val arbitraryWorkerId: Arbitrary[WorkerId] = Arbitrary(Gen.alphaNumStr.map(WorkerId))



  "scheduler" must {
    "give empty work" in {
      unitTest[WorkerId, Work, Unit](SchedulerEndpoints.fetchWorkEndpoint, Right(NoWork))(
        Uri.apply("http", "localhost", 8080)
      )
    }
  }

}
