package flerken.worker

import java.util.concurrent.atomic.AtomicReference

import cats.data.Kleisli
import cats.effect._
import flerken.worker.Protocol._
import org.scalacheck.Arbitrary
import org.scalatest.{Matchers, WordSpec}

import scala.util.Random

class WorkerSpec extends WordSpec with Matchers {


  lazy val randomWorkPayload = Work(Arbitrary.arbString.arbitrary.sample.get)
  lazy val expectedResult = Result(randomWorkPayload.payload.hashCode.toString)
  "a worker" can {
    lazy val workId = worker.task.run(randomWorkPayload).unsafeRunSync().workId

    "notify for pending work" in {
      val expectedWorkId = workId
      pendingWorkReceived.get() shouldBe Some(PendingWork(expectedWorkId, randomWorkPayload))
    }

    "notify for completed work" in {
      workCompleted.get() shouldBe Some(WorkCompleted(workId, expectedResult))
    }

  }

  case class Work(payload: String)
  case class Result(payload: String)
  lazy val pendingWorkReceived = new AtomicReference[Option[Notification]](None)
  lazy val workCompleted = new AtomicReference[Option[Notification]](None)

  lazy val testNotifier = Kleisli[IO, Notification, NotificationAck] {
    notification => IO {
      notification match {
        case wc: WorkCompleted[_] =>
          workCompleted.set(Some(wc))
        case pendingWork: PendingWork[_] =>
          pendingWorkReceived.set(Some(pendingWork))
      }
      NotificationAck(notification.workId)
    }
  }

  implicit object UniqueWork extends Unique[IO, Work] {
    override def id: IO[String] = IO.pure(Random.alphanumeric.take(32).mkString)
  }

  lazy val testExecutor = Kleisli[IO, Work, Result] {
    work =>
      IO(Result(work.payload.hashCode.toString))
  }

  lazy val worker: Worker[IO, Work, Result] = new Worker[IO, Work, Result](
    testNotifier,
    testExecutor
  )


}
