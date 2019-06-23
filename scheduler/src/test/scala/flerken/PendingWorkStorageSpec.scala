package flerken

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import akka.actor.typed.eventstream.Subscribe
import org.scalatest.{Matchers, WordSpec}

class PendingWorkStorageSpec extends WordSpec with Matchers with AkkaBase {

  import PendingWorkStorage._

  "storage" must {
    val sender = testKit.createTestProbe[Work]()
    val pendingWorkEventListener = testKit.createTestProbe[Event]()
    testKit.system.eventStream ! Subscribe(pendingWorkEventListener.ref)
    val firstWorkIdentifier = new AtomicReference[UUID]()

    val storage = testKit.spawn(PendingWorkStorage.behavior, "WorkStorage")
    "give no work if there is none" in {

      storage ! FetchWork(sender.ref)
      sender.expectMessage(NoWork)

    }


    "assign identifiers to work added" in {
      val workSender = testKit.createTestProbe[WorkAck]()
      storage ! AddWork[String]("some work", workSender.ref)
      val ref = workSender.expectMessageType[WorkAck].identifier
      firstWorkIdentifier.set(ref)
    }

    "allocate work to incoming worker" in {
      storage ! FetchWork(sender.ref)
      sender.expectMessage(DoWork(firstWorkIdentifier.get(), "some work"))
      storage ! FetchWork(sender.ref)
      sender.expectMessage(NoWork)
    }

    "allocate work in fifo fashion" in {
      val workSender = testKit.createTestProbe[WorkAck]()

      storage ! AddWork[String]("work 1", workSender.ref)
      val work1UUID = workSender.expectMessageType[WorkAck].identifier
      storage ! AddWork[String]("work 2", workSender.ref)
      val work2UUID = workSender.expectMessageType[WorkAck].identifier

      storage ! FetchWork(sender.ref)
      sender.expectMessage(DoWork(work1UUID, "work 1"))
      storage ! FetchWork(sender.ref)
      sender.expectMessage(DoWork(work2UUID, "work 2"))
      storage ! FetchWork(sender.ref)
      sender.expectMessage(NoWork)
    }

    "re-scheduled any failed work" in {
      storage ! WorkFailed(firstWorkIdentifier.get())

      pendingWorkEventListener.expectMessage(WorkRetry(firstWorkIdentifier.get()))

      storage ! FetchWork(sender.ref)

      sender.expectMessage(DoWork(firstWorkIdentifier.get(), "some work"))
    }
  }

}
