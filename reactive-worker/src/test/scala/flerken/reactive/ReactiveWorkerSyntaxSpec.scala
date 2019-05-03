package flerken.reactive

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, Behavior}
import cats.data.Kleisli
import cats.effect.IO
import flerken.worker.Protocol.{SubmitWork, WorkCompleted, WorkId}
import org.scalacheck.Arbitrary
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

class ReactiveWorkerSyntaxSpec extends FlatSpec with Matchers with BeforeAndAfterAll{

  val testKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()

  case object A
  case object B
  val kleisli = Kleisli[IO, A.type, B.type] {
    _ => IO.pure(B)
  }
  def behaviour: Behavior[SubmitWork[A.type]] = ReactiveWorker.fromKleisli(kleisli)


  case object C

  def kleisliNext = Kleisli[IO, B.type, C.type] {
    _ => IO.pure(C)
  }


  def composedBehaviour: Behavior[SubmitWork[A.type]] = kleisli andThenReactive kleisliNext

  "single kleisli" can "be converted to reactive via event stream" in {
    val workId = WorkId(Arbitrary.arbString.arbitrary.sample.get)
    val actorA: ActorRef[SubmitWork[A.type]] = testKit.spawn(behaviour)
    val eventListener: TestProbe[WorkCompleted[B.type]] = testKit.createTestProbe()
    testKit.system.toUntyped.eventStream.subscribe(eventListener.ref.toUntyped, classOf[WorkCompleted[B.type]])
    actorA ! SubmitWork(workId, A)
    eventListener.expectMessage(WorkCompleted(workId, B))
  }

  "composing kleisli reactively" must "compose actor behaviours" in {
    val workId = WorkId(Arbitrary.arbString.arbitrary.sample.get)

    val composedActor: ActorRef[SubmitWork[A.type]] = testKit.spawn(composedBehaviour)
    val eventListenerC: TestProbe[WorkCompleted[C.type]] = testKit.createTestProbe()
    testKit.system.toUntyped.eventStream.subscribe(eventListenerC.ref.toUntyped, classOf[WorkCompleted[C.type]])
    composedActor ! SubmitWork(workId, A)
    eventListenerC.expectMessage(WorkCompleted(workId, C))
  }
}
