package com.raphtory.core.actors.orchestration.clustermanager

import akka.actor.ActorRef
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import com.raphtory.core.actors.RaphtoryActor
import com.raphtory.core.actors.orchestration.clustermanager.WatermarkManager.Message.{SaveState, WatermarkTime}

class SnapshotManager(managerCount: Int) extends RaphtoryActor {
  val mediator: ActorRef = DistributedPubSub(context.system).mediator
  mediator ! DistributedPubSubMediator.Put(self)

  override def receive: Receive = {
    case WatermarkTime(time:Long) => saveState(time)
  }

  def saveState(time:Long) =
    getAllWriters().foreach { workerPath =>mediator ! new DistributedPubSubMediator.Send(workerPath, SaveState)}
}


//