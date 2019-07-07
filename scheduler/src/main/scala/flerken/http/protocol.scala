package flerken.http

import flerken.protocol.Protocol._
import io.circe.{Decoder, Encoder}

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
        }
      } yield (result)
  }

  implicit val workResultEncoder: Encoder[WorkResult] = Encoder.instance {
    case uwr: UncompletedWorkResult => uncompletedWorkResultEncoder(uwr)
  }


  implicit val storeWorkEncoder: Encoder[StoreWork] = deriveEncoder[StoreWork]
  implicit val storeWorkDecoder: Decoder[StoreWork] = deriveDecoder[StoreWork]


}