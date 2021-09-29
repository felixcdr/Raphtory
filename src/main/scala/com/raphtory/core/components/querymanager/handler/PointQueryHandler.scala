package com.raphtory.core.components.querymanager.handler

import com.raphtory.core.components.querymanager.{PerspectiveController, QueryHandler}
import com.raphtory.core.model.algorithm.GraphAlgorithm

case class PointQueryHandler(jobID: String, algorithm:GraphAlgorithm, timestamp:Long, windows:List[Long]) extends QueryHandler(jobID,algorithm) {
  override def receive: Receive = ???

  override protected def buildSubTaskController(latestTimestamp: Long): PerspectiveController =
    PerspectiveController.pointQueryController(timestamp,windows)
}
