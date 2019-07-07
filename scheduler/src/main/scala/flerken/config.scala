package flerken

import flerken.protocol.Protocol.WorkerId

import scala.concurrent.duration.FiniteDuration

case class StorageConfig(
              staleTimeout: FiniteDuration,
              workCompletionTimeout: FiniteDuration,
              highWatermark: Int,
              identifier: WorkerId
)