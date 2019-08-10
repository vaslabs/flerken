package flerken.http

import com.softwaremill.sttp.{Uri, _}
import org.scalacheck.Arbitrary
import org.scalatest.{Assertion, Matchers}
import tapir.Endpoint
import tapir.client.sttp._

object IntegrationBase  extends Matchers with TapirSttpClient{


  def assertion[I, O, E](
    serverEndpoint: Endpoint[I, E, O, Nothing], expectation: => Either[E, O])(baseUri: Uri)(
      implicit arbitraryInput: Arbitrary[I]): Assertion =
    fetchResult(serverEndpoint)(baseUri) shouldBe expectation

  def chainedAssertion[I, O, E](
                        serverEndpoint: Endpoint[I, E, O, Nothing], expectation: => Either[E, O])(baseUri: Uri)(
                        implicit arbitraryInput: Arbitrary[I]): (I, Assertion) = {
    val (input, output) = chain(serverEndpoint)(baseUri)
    input -> (output shouldBe expectation)
  }


  def fetchResult[I, O, E](
                         serverEndpoint: Endpoint[I, E, O, Nothing])(baseUri: Uri)(
                         implicit arbitraryInput: Arbitrary[I]): Either[E, O] =
    chain(serverEndpoint)(baseUri)._2


  def chain[I, O, E]( serverEndpoint: Endpoint[I, E, O, Nothing])(baseUri: Uri)(
    implicit arbitraryInput: Arbitrary[I]): (I, Either[E, O]) = {
    val client = serverEndpoint.toSttpRequest(
      baseUri
    )

    implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

    val requestInput = arbitraryInput.arbitrary.sample.get

    val request: Request[Either[E, O], Nothing] = client(requestInput)

    requestInput -> request.send[Id]().unsafeBody
  }
}
