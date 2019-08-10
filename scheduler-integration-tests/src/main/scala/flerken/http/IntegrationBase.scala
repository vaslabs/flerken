package flerken.http

import com.softwaremill.sttp.{Uri, _}
import org.scalacheck.Arbitrary
import org.scalatest.{Assertion, Matchers}
import tapir.Endpoint
import tapir.client.sttp._

object IntegrationBase  extends Matchers with TapirSttpClient{


  def unitTest[I, O, E](
    serverEndpoint: Endpoint[I, E, O, Nothing], expectation: => Either[E, O])(baseUri: Uri)(
      implicit arbitraryInput: Arbitrary[I]): Assertion = {

    val client = serverEndpoint.toSttpRequest(
      baseUri
    )

    implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()


    val requestInput = arbitraryInput.arbitrary.sample.get

    val request: Request[Either[E, O], Nothing] = client(requestInput)

    request.send[Id]().unsafeBody shouldBe expectation
  }

}
