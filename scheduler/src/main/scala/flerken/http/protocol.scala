package flerken.http

import flerken.protocol.Protocol._
import io.circe.{Decoder, Encoder, Json}

object json_support {
  import io.circe.generic.semiauto._
  import io.circe.generic.auto._
  implicit val workEncoder: Encoder[Work] = deriveEncoder[Work]
  implicit val workDecoder: Decoder[Work] = deriveDecoder[Work]

  implicit val workerIdEncoder: Encoder[WorkerId] = Encoder.encodeString.contramap(_.id)
  implicit val workerIdDecoder: Decoder[WorkerId] = Decoder.decodeString.map(WorkerId)

  implicit val workIdEncoder: Encoder[WorkId] = Encoder.encodeUUID.contramap(_.value)
  implicit val workIdDecoder: Decoder[WorkId] = Decoder.decodeUUID.map(WorkId)


  implicit val workStatusDecoder: Decoder[WorkStatus] = deriveDecoder[WorkStatus]
  implicit val workStatusEncoder: Encoder[WorkStatus] = deriveEncoder[WorkStatus]

  implicit val uncompletedWorkResultEncoder: Encoder[UncompletedWorkResult] = deriveEncoder[UncompletedWorkResult]
  implicit val uncompletedWorkResultDecoder: Decoder[UncompletedWorkResult] = deriveDecoder[UncompletedWorkResult]


  implicit val workResultDecoder: Decoder[WorkResult] = Decoder.instance {
    hCursor =>
      for {
        status <- hCursor.downField("status").as[WorkStatus]
        workId <- hCursor.downField("workId").as[WorkId]
        result = status match {
          case Pending => WorkResult.pending(workId)
          case Completed =>
            val payload = hCursor.downField("result").as[Json].getOrElse(Json.obj())
            WorkResult.completed(workId, payload)
        }
      } yield result
  }

  implicit val workResultEncoder: Encoder[WorkResult] = Encoder.instance {
    case uwr: UncompletedWorkResult => uncompletedWorkResultEncoder(uwr)
    case completedWorkResult: CompletedWorkResult => deriveEncoder[CompletedWorkResult].apply(completedWorkResult)
  }


  implicit val storeWorkEncoder: Encoder[StoreWork] = deriveEncoder[StoreWork]
  implicit val storeWorkDecoder: Decoder[StoreWork] = deriveDecoder[StoreWork]

  implicit val storeWorkResultEncoder: Encoder[StoreWorkResult] = deriveEncoder[StoreWorkResult]
  implicit val storeWorkResultDecoder: Decoder[StoreWorkResult] = deriveDecoder[StoreWorkResult]

  implicit val resultRejectedEncoder: Encoder[ResultRejected] = deriveEncoder[ResultRejected]
  implicit val resultRejectedDecoder: Decoder[ResultRejected] = deriveDecoder[ResultRejected]

}