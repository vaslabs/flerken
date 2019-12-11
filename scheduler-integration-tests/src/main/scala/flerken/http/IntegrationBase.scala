package flerken.http

import cats.Id
import sttp.client._
import org.scalacheck.Arbitrary
import org.scalatest.{Assertion, Matchers}
import sttp.model.Uri
import sttp.tapir.Endpoint
import sttp.tapir.client.sttp._

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
    val client = serverEndpoint.toSttpRequestUnsafe(
      baseUri
    )

    implicit val backend = HttpURLConnectionBackend()

    val requestInput = arbitraryInput.arbitrary.sample.get

    val request: Request[Either[E, O], Nothing] = client(requestInput)

    requestInput -> request.send[Id]().body
  }
}
