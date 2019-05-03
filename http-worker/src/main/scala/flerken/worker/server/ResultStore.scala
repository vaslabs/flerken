package flerken.worker.server

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import flerken.worker.Protocol._
import io.circe.{Decoder, Encoder, HCursor}

import scala.concurrent.Future

class ResultStore[Outcome](
      actorSystem: ActorSystem[_],
      resultStore: ActorRef[ResultStore.Query[Outcome]])(implicit
      encoder: Encoder[Outcome],
      decoder: Decoder[Outcome],
      timeout: Timeout) {

  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
  private implicit val scheduler = actorSystem.scheduler

  private[flerken] val jsonSupport = new ResultStore.JsonSupport[Outcome]()
  import jsonSupport._

  val route: Route = pathSingleSlash {
    get {
      headerValueByName("X-FLERKEN-WORK-ID") {
        id =>
          val workId = WorkId(id)
          val resultFuture: Future[Notification[Outcome]] =
            resultStore ?[Notification[Outcome]] (ref => ResultStore.FetchOutcome(workId, ref))
          complete(resultFuture)
      }
    }
  }

}

object ResultStore {

  sealed trait Query[Outcome]

  sealed trait ReadQuery[Outcome] extends Query[Outcome]


  case class FetchOutcome[Outcome](
      workId: WorkId,
      replyTo: ActorRef[Notification[Outcome]]
  ) extends ReadQuery[Outcome]


  sealed trait UpdateQuery[Outcome] extends Query[Outcome]

  private[flerken] case class UpdateWork[Outcome](value: Notification[Outcome]) extends UpdateQuery[Outcome]


  private[flerken] def behavior[Outcome](
          state: Notification[Outcome]): Behavior[Query[Outcome]] =
    Behaviors.setup[Query[Outcome]] { ctx =>
      ctx.spawnAnonymous(notificationListenerBehavior(ctx.self))
      Behaviors.receiveMessage[Query[Outcome]] {
        case FetchOutcome(_, replyTo) =>
          replyTo ! state
          Behaviors.same
        case UpdateWork(notification) => behavior(notification)
      }
    }

  private def notificationListenerBehavior[Outcome](
      replyTo: ActorRef[UpdateWork[Outcome]]): Behavior[Notification[Outcome]] =
    Behaviors.setup {
      ctx =>
        ctx.system.toUntyped.eventStream.subscribe(ctx.self.toUntyped, classOf[Notification[Outcome]])
        Behaviors.receiveMessage {
          case notification =>
            replyTo ! UpdateWork(notification)
            Behaviors.same
        }
    }

  class JsonSupport[A] private[ResultStore](implicit val encoder: Encoder[A], val decoder: Decoder[A]) {
    import flerken.worker.http.Worker.json_support._
    import io.circe.generic.semiauto._
    import io.circe.syntax._

    private final def addState[A](encoder: Encoder[A], state: String): Encoder[A] =
      encoder.mapJson(_.mapObject(_.add("state", state.asJson)))

    private implicit val workCompletedEncoder: Encoder[WorkCompleted[A]] =
      addState[WorkCompleted[A]](deriveEncoder[WorkCompleted[A]], "completed")


    private implicit val pendingWorkEncoder: Encoder[PendingWork[A]] =
      addState(deriveEncoder, "pending")

    private implicit val emptyWorkEncoder: Encoder[EmptyWork[A]] =
      addState(deriveEncoder, "empty")
    private implicit val workErrorEncoder: Encoder[WorkError[A]] =
      addState(deriveEncoder, "error")

    implicit val notificationEncoder: Encoder[Notification[A]] = (a: Notification[A]) => a match {
      case wc: WorkCompleted[A] => wc.asJson
      case pendingWork: PendingWork[A] => pendingWork.asJson
      case emptyWork: EmptyWork[A] => emptyWork.asJson
      case error: WorkError[A] => error.asJson
    }

    private implicit val workCompletedDecoder = deriveDecoder[WorkCompleted[A]]
    private implicit val pendingWorkDecoder = deriveDecoder[PendingWork[A]]
    private implicit val emptyWorkDecoder = deriveDecoder[EmptyWork[A]]
    private implicit val workErrorDecoder = deriveDecoder[WorkError[A]]

    implicit val notificationDecoder: Decoder[Notification[A]] = (cursor: HCursor) =>
      cursor.downField("state").as[String] match {
        case Right(status) =>
          status match {
            case "completed" => cursor.as[WorkCompleted[A]]
            case "pending" => cursor.as[PendingWork[A]]
            case "empty" => cursor.as[EmptyWork[A]]
            case "error" => cursor.as[WorkError[A]]
          }
        case Left(decodingFailure) => Left(decodingFailure)
      }





  }

}