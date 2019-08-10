package flerken.http

import com.softwaremill.sttp.Uri
import org.scalacheck.Gen
import org.scalatest.{Assertion, Matchers}
import tapir.Endpoint
import tapir.server.akkahttp._
import tapir.client.sttp._

object TestExport extends TapirAkkaHttpServer with Matchers{

  def unitTest[I, O, E](
    serverEndpoint: Endpoint[I, O, E, Nothing], expectation: O)(baseUri: Uri)(
      implicit akkaHttpServerOptions: AkkaHttpServerOptions,
      gen: Gen[I]): Assertion = {
    val client = serverEndpoint.toSttpRequest(
      baseUri
    )

    val requestInput = gen.sample.get

    client(requestInput) shouldBe expectation

  }

}
