package flerken

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Scheduler}
import akka.util.Timeout
import flerken.PendingWorkStorage.{WorkRejected, _}
import flerken.ResultStorage.{NotReady, ResultAccepted}
import flerken.protocol.Protocol
import flerken.protocol.Protocol.WorkResult
import io.circe.Json

import scala.concurrent.{ExecutionContext, Future}

class ActorBasedSchedulerApi(
                                workStorage: ActorRef[WorkerGroup.Protocol],
                                resultStorage: ActorRef[ResultStorage.Protocol])(implicit
                                messageReceivedTimeout: Timeout,
                                scheduler: Scheduler) extends SchedulerApi {

  private implicit val executionContext: ExecutionContext = ExecutionContext.global


  override def fetchWork(workerId: Protocol.WorkerId): Future[Either[Unit, Protocol.Work]] =
    (workStorage ?[Work] (ref => WorkerGroup.AssignWorkTo(workerId, ref))).map {
      case NoWork =>
        Right(Protocol.NoWork)
      case DoWork(id, json: Json) =>
        Right(Protocol.SomeWork(id, json))
      case _ =>
        Left(())
    }

  override def storeWork(storeWork: Protocol.StoreWork): Future[Either[Unit, Protocol.WorkId]] =
    (workStorage ?[WorkAck] (ref => WorkerGroup.StoreWorkFor(storeWork.workerId, storeWork.work, ref))).map {
      case WorkReceived(workId) =>
        Right(workId)
      case WorkRejected => Left(())
    }

  override def fetchWorkResult(workId: Protocol.WorkId): Future[Either[Unit, Protocol.WorkResult]] =
    (resultStorage ?[Option[WorkResult]] (ref => ResultStorage.FetchResult(workId, ref))).map {
      case None =>
        Left(())
      case Some(workResult) =>
        Right(workResult)
    }

  override def storeWorkResult(storeWorkResult: Protocol.StoreWorkResult): Future[Either[Protocol.ResultRejected, Unit]] =
    (resultStorage ?[ResultStorage.ResultAck]
      (ref => ResultStorage.StoreResult(storeWorkResult.workId, storeWorkResult.result, ref))).map {
      case ResultAccepted =>
        Right(())
      case ResultStorage.DuplicateResult =>
        Left(Protocol.ResultRejected(s"Duplicated result, rejecting most recent one for workId ${storeWorkResult.workId}"))
      case NotReady =>
        Left(Protocol.ResultRejected(
          s"Result storage is not ready to receive this result, please retry fast on this occasion, workId: ${storeWorkResult.workId}")
        )
   }
}
