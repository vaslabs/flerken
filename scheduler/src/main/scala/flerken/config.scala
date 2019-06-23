package flerken

import scala.concurrent.duration.FiniteDuration

case class StorageConfig(
              staleTimeout: FiniteDuration,
              workCompletionTimeout: FiniteDuration
)