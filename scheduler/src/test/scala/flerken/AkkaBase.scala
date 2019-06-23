package flerken

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.{BeforeAndAfterAll, Suite}

trait AkkaBase extends BeforeAndAfterAll{this: Suite=>

  val testKit = ActorTestKit()

  override def afterAll() = testKit.shutdownTestKit()
}
