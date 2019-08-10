package flerken

import akka.actor.typed.eventstream.EventStream.Subscribe
import flerken.PendingWorkStorage._
import flerken.protocol.Protocol.WorkerId
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._
import scala.util.Random

class PendingWorkStorageLimitsSpec extends WordSpec with Matchers with AkkaBase {

  "work storage" must {
    val pendingWorkStorageEventListener = testKit.createTestProbe[Event]()
    testKit.system.eventStream ! Subscribe(pendingWorkStorageEventListener.ref)
    val storageConfig = StorageConfig(5 seconds, 1 second, 5, WorkerId("PendingWorkStorageLimitsSpec"))
    val storage = testKit.spawn(
      PendingWorkStorage.behavior(storageConfig),
      "PendingWorkStorage"
    )
    "reject work when it reaches high watermark" in {
      val workSender = testKit.createTestProbe[WorkAck]()
      Stream.continually(Random.nextString(32)).take(storageConfig.highWatermark).foreach {
        work =>
          storage ! PendingWorkStorage.AddWork(work, workSender.ref)
          workSender.expectMessageType[WorkReceived]
      }

      pendingWorkStorageEventListener.expectMessage(HighWatermarkReached(storageConfig.identifier))

      storage ! PendingWorkStorage.AddWork("will be rejected", workSender.ref)
      workSender.expectMessage(PendingWorkStorage.WorkRejected)
    }

    "continue accepting work once below high watermark" in {
      val workSender = testKit.createTestProbe[Any]()
      storage ! FetchWork(workSender.ref)
      val work = workSender.expectMessageType[DoWork[_]]
      storage ! CompleteWork(work.id)
      storage ! PendingWorkStorage.AddWork("will not be rejected", workSender.ref)
      workSender.expectMessageType[WorkReceived]
    }
  }

}
