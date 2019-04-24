package flerken.reactive

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, Behavior}
import cats.data.Kleisli
import cats.effect.IO
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

class ReactiveWorkerSyntaxSpec extends FlatSpec with Matchers with BeforeAndAfterAll{

  val testKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()

  case object A
  case object B
  val kleisli = Kleisli[IO, A.type, B.type] {
    _ => IO.pure(B)
  }
  def behaviour: Behavior[A.type] = ReactiveWorker.fromKleisli(kleisli)


  case object C

  def kleisliNext = Kleisli[IO, B.type, C.type] {
    _ => IO.pure(C)
  }

  def composedBehaviour: Behavior[A.type] = kleisli andThenReactive kleisliNext

  "single kleisli" can "be converted to reactive via event stream" in {
    val actorA: ActorRef[A.type] = testKit.spawn(behaviour)
    val eventListener: TestProbe[B.type] = testKit.createTestProbe()
    testKit.system.toUntyped.eventStream.subscribe(eventListener.ref.toUntyped, B.getClass)
    actorA ! A
    eventListener.expectMessage(B)
  }

  "composing kleisli reactively" must "compose actor behaviours" in {
    val composedActor: ActorRef[A.type] = testKit.spawn(composedBehaviour)
    val eventListenerB: TestProbe[B.type] = testKit.createTestProbe()
    val eventListenerC: TestProbe[C.type] = testKit.createTestProbe()
    testKit.system.toUntyped.eventStream.subscribe(eventListenerB.ref.toUntyped, B.getClass)
    testKit.system.toUntyped.eventStream.subscribe(eventListenerC.ref.toUntyped, C.getClass)
    composedActor ! A
    eventListenerB.expectMessage(B)
    eventListenerC.expectMessage(C)
  }
}
