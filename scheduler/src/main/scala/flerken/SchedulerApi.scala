package flerken


import flerken.protocol.Protocol._

import scala.concurrent.Future

trait SchedulerApi {

  def fetchWork(workerId: WorkerId): Future[Either[Unit, Work]]

  def storeWork(storeWork: StoreWork): Future[Either[Unit, WorkId]]

  def fetchWorkResult(workId: WorkId): Future[Either[Unit, WorkResult]]

  def storeWorkResult(storeWorkResult: StoreWorkResult): Future[Either[ResultRejected, Unit]]
}
