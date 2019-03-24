package flerken.worker

import java.util.concurrent.atomic.AtomicReference

import cats.data.Kleisli
import cats.effect._
import flerken.worker.Protocol._
import org.scalacheck.Arbitrary
import org.scalatest.{Matchers, WordSpec}

import scala.util.Random

class WorkerSpec extends WordSpec with Matchers {
  import WorkerSpec._
  implicit val unique: Unique[IO, Work] = UniqueWork

  "a worker" can {
    val expectedWorkPayload = randomWorkPayload
    lazy val expectedResult = Result(expectedWorkPayload.payload.hashCode.toString)

    lazy val workId = worker.task.run(expectedWorkPayload).unsafeRunSync().workId

    "notify for pending work" in {
      val expectedWorkId = workId
      pendingWorkReceived.get() shouldBe Some(PendingWork(expectedWorkId, expectedWorkPayload))
    }

    "notify for completed work" in {
      workCompleted.get() shouldBe Some(WorkCompleted(workId, expectedResult))
    }

    "notify for errors" in {
      val errorWorker: Worker[IO, Work, Result] = new Worker[IO, Work, Result](
        testNotifier,
        Kleisli[IO, Work, Result] {
          _ => IO.raiseError(new RuntimeException)
        }
      )
      val notificationAck = errorWorker.task.run(randomWorkPayload).unsafeRunSync()
      errors.get() shouldBe Some(WorkError(notificationAck.workId))
    }

  }

}

object WorkerSpec {
  def randomWorkPayload = Work(Arbitrary.arbString.arbitrary.sample.get)

  case class Work(payload: String)
  case class Result(payload: String)

  implicit object UniqueWork extends Unique[IO, Work] {
    override def id: IO[String] = IO.pure(Random.alphanumeric.take(32).mkString)
  }

  lazy val pendingWorkReceived = new AtomicReference[Option[Notification]](None)
  lazy val workCompleted = new AtomicReference[Option[Notification]](None)
  lazy val errors = new AtomicReference[Option[WorkError]]()


  lazy val worker: Worker[IO, Work, Result] = new Worker[IO, Work, Result](
    testNotifier,
    testExecutor
  )
  lazy val testNotifier = Kleisli[IO, Notification, NotificationAck] {
    notification => IO {
      notification match {
        case wc: WorkCompleted[_] =>
          workCompleted.set(Some(wc))
        case pendingWork: PendingWork[_] =>
          pendingWorkReceived.set(Some(pendingWork))
        case workError: WorkError =>
          errors.set(Some(workError))
      }
      NotificationAck(notification.workId)
    }
  }


  lazy val testExecutor = Kleisli[IO, Work, Result] {
    work =>
      IO(Result(work.payload.hashCode.toString))
  }
}