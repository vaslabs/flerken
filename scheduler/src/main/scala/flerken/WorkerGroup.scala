package flerken

import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.ShardingMessageExtractor
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import flerken.PendingWorkStorage.WorkAck
import flerken.http.Protocol.WorkerId

object WorkerGroup {

  val TypeKey = EntityTypeKey[PendingWorkStorage.Protocol]("WorkStorage")

  final val Distribution = 200

  def shardRegion(
               storageConfig: StorageConfig,
               sharding: ClusterSharding): ActorRef[Protocol] = sharding.init(
    Entity(TypeKey, _ => PendingWorkStorage.behavior(storageConfig)).withMessageExtractor[Protocol](
      new ShardingMessageExtractor[Protocol, PendingWorkStorage.Protocol]  {
        override def entityId(message: Protocol): String =
          message.workerId.id

        override def shardId(entityId: String): String =
          (Math.abs(entityId.hashCode) % Distribution).toString

        override def unwrapMessage(message: Protocol): PendingWorkStorage.Protocol =
          message match {
            case AssignWorkTo(_, replyTo) =>
              PendingWorkStorage.FetchWork(replyTo)
            case StoreWorkFor(_, work, replyTo: ActorRef[WorkAck]) =>
              PendingWorkStorage.AddWork(work, replyTo)
          }
      }
    )
  )


  sealed trait Protocol {
    def workerId: WorkerId
  }
  case class AssignWorkTo(workerId: WorkerId, replyTo: ActorRef[Any]) extends Protocol
  case class StoreWorkFor[W](workerId: WorkerId, work: W, replyTo: ActorRef[WorkAck]) extends Protocol

}
