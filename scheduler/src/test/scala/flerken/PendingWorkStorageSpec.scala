package flerken

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import akka.actor.typed.eventstream.Subscribe
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._

class PendingWorkStorageSpec extends WordSpec with Matchers with AkkaBase {

  import PendingWorkStorage._

  "storage" must {
    val storageConfig = StorageConfig(5 seconds, 2 seconds)
    val sender = testKit.createTestProbe[Work]()
    val pendingWorkEventListener = testKit.createTestProbe[Event]()
    testKit.system.eventStream ! Subscribe(pendingWorkEventListener.ref)
    val firstWorkIdentifier = new AtomicReference[UUID]()

    val storage = testKit.spawn(PendingWorkStorage.behavior(storageConfig), "WorkStorage")
    "give no work if there is none" in {

      storage ! FetchWork(sender.ref)
      sender.expectMessage(NoWork)

    }


    "assign identifiers to work added" in {
      val workSender = testKit.createTestProbe[WorkAck]()
      storage ! AddWork[String]("some work", workSender.ref)
      val id = workSender.expectMessageType[WorkAck].identifier
      firstWorkIdentifier.set(id)
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
      storage ! CompleteWork(work1UUID)
      pendingWorkEventListener.expectMessage(WorkCompleted(work1UUID))
      storage ! CompleteWork(work2UUID)
      pendingWorkEventListener.expectMessage(WorkCompleted(work2UUID))
    }

    "re-scheduled any failed work" in {
      storage ! WorkFailed(firstWorkIdentifier.get())

      pendingWorkEventListener.expectMessage(WorkRetry(firstWorkIdentifier.get()))

      storage ! FetchWork(sender.ref)

      sender.expectMessage(DoWork(firstWorkIdentifier.get(), "some work"))
    }

    "not re-schedule completed work" in {
      storage ! CompleteWork(firstWorkIdentifier.get())
      pendingWorkEventListener.expectMessage(WorkCompleted(firstWorkIdentifier.get()))
      storage ! WorkFailed(firstWorkIdentifier.get())
      pendingWorkEventListener.expectNoMessage(200 milli)
      storage ! FetchWork(sender.ref)
      sender.expectMessage(NoWork)
    }

    "expire the work when it becomes stale" in {
      val workSender = testKit.createTestProbe[WorkAck]()
      storage ! AddWork("work to become stale", workSender.ref)
      val workId = workSender.expectMessageType[WorkAck].identifier
      pendingWorkEventListener.expectMessage(
        storageConfig.staleTimeout + 1.second, PendingWorkExpired(workId)
      )
      storage ! FetchWork(sender.ref)
      sender.expectMessage(NoWork)
    }

    "re-schedule allocated work when it times out" in {
      val workSender = testKit.createTestProbe[WorkAck]()
      storage ! AddWork("work to timeout", workSender.ref)
      val workId = workSender.expectMessageType[WorkAck].identifier
      storage ! FetchWork(sender.ref)
      sender.expectMessage(DoWork(workId, "work to timeout"))
      pendingWorkEventListener.expectMessage(
        storageConfig.workCompletionTimeout + 1.second,
        WorkCompletionTimedOut(workId)
      )
      pendingWorkEventListener.expectMessage(
        WorkRetry(workId)
      )
      storage ! FetchWork(sender.ref)
      sender.expectMessage(DoWork(workId, "work to timeout"))
      storage ! CompleteWork(workId)
      pendingWorkEventListener.expectMessage(WorkCompleted(workId))
    }

  }

}
