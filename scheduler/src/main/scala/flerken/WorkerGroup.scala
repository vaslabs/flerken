package flerken

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.ShardingMessageExtractor
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import flerken.PendingWorkStorage.{Work, WorkAck}
import flerken.protocol.Protocol.WorkerId

import scala.concurrent.duration.FiniteDuration

object WorkerGroup {

  val TypeKey = EntityTypeKey[PendingWorkStorage.Protocol]("WorkStorage")

  final val Distribution = 200

  def toStorageConfig(workerGroupConfig: Config, entity: String): StorageConfig =
    StorageConfig(
      workerGroupConfig.staleTimeout,
      workerGroupConfig.workCompletionTimeout,
      workerGroupConfig.highWatermark, WorkerId(entity)
    )

  def shardRegion(
                   workerGroupConfig: Config,
                   sharding: ClusterSharding): ActorRef[Protocol] = sharding.init(
    Entity(TypeKey, entity => PendingWorkStorage.behavior(toStorageConfig(workerGroupConfig, entity.entityId))).withMessageExtractor[Protocol](
      new ShardingMessageExtractor[Protocol, PendingWorkStorage.Protocol]  {
        override def entityId(message: Protocol): String =
          message.workerId.id

        override def shardId(entityId: String): String =
          (Math.abs(entityId.hashCode) % Distribution).toString

        override def unwrapMessage(message: Protocol): PendingWorkStorage.Protocol =
          message match {
            case AssignWorkTo(_, replyTo) =>
              PendingWorkStorage.FetchWork(replyTo)
            case StoreWorkFor(_, work, replyTo) =>
              PendingWorkStorage.AddWork(work, replyTo)
          }
      }
    )
  )


  sealed trait Protocol {
    def workerId: WorkerId
  }
  case class AssignWorkTo(workerId: WorkerId, replyTo: ActorRef[Work]) extends Protocol
  case class StoreWorkFor[W](workerId: WorkerId, work: W, replyTo: ActorRef[WorkAck]) extends Protocol


  case class Config(
      staleTimeout: FiniteDuration,
      workCompletionTimeout: FiniteDuration,
      highWatermark: Int
  )
}
