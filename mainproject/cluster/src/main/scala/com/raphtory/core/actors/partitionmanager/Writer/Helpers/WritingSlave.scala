package com.raphtory.core.actors.partitionmanager.Writer.Helpers

import akka.actor.{Actor, ActorRef}
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import com.raphtory.core.model.communication._
import com.raphtory.core.storage.EntityStorage

class WritingSlave(workerID:Int) extends Actor {
  val mediator              : ActorRef = DistributedPubSub(context.system).mediator // get the mediator for sending cluster messages
  mediator ! DistributedPubSubMediator.Put(self)
  //println(akka.serialization.Serialization.serializedActorPath(self))

  override def receive:Receive = {
    case VertexAdd(routerID,msgTime,srcId)                                => {EntityStorage.vertexAdd(routerID,msgTime,srcId);                                  vHandle(srcId,msgTime)}
    case VertexRemoval(routerID,msgTime,srcId)                            => {EntityStorage.vertexRemoval(routerID,msgTime,srcId);                              vHandle(srcId,msgTime)}
    case VertexAddWithProperties(routerID,msgTime,srcId,properties)       => {EntityStorage.vertexAdd(routerID,msgTime,srcId,properties);                       vHandle(srcId,msgTime)}

    case EdgeAdd(routerID,msgTime,srcId,dstId)                            => {EntityStorage.edgeAdd(routerID,msgTime,srcId,dstId);                              eHandle(srcId,dstId,msgTime)}
    case RemoteEdgeAdd(routerID,msgTime,srcId,dstId,properties)           => {EntityStorage.remoteEdgeAdd(routerID,msgTime,srcId,dstId,properties);             eHandleSecondary(srcId,dstId,msgTime)}
    case RemoteEdgeAddNew(routerID,msgTime,srcId,dstId,properties,deaths) => {EntityStorage.remoteEdgeAddNew(routerID,msgTime,srcId,dstId,properties,deaths);   eHandleSecondary(srcId,dstId,msgTime)}
    case EdgeAddWithProperties(routerID,msgTime,srcId,dstId,properties)   => {EntityStorage.edgeAdd(routerID,msgTime,srcId,dstId,properties);                   eHandle(srcId,dstId,msgTime)}

    case EdgeRemoval(routerID,msgTime,srcId,dstId)                        => {EntityStorage.edgeRemoval(routerID,msgTime,srcId,dstId);                          eHandle(srcId,dstId,msgTime)}
    case RemoteEdgeRemoval(routerID,msgTime,srcId,dstId)                  => {EntityStorage.remoteEdgeRemoval(routerID,msgTime,srcId,dstId);                    eHandleSecondary(srcId,dstId,msgTime)}
    case RemoteEdgeRemovalNew(routerID,msgTime,srcId,dstId,deaths)        => {EntityStorage.remoteEdgeRemovalNew(routerID,msgTime,srcId,dstId,deaths);          eHandleSecondary(srcId,dstId,msgTime)}

    case ReturnEdgeRemoval(routerID,msgTime,srcId,dstId)                  => {EntityStorage.returnEdgeRemoval(routerID,msgTime,srcId,dstId);                    eHandleSecondary(srcId,dstId,msgTime)}
    case RemoteReturnDeaths(msgTime,srcId,dstId,deaths)                   => {EntityStorage.remoteReturnDeaths(msgTime,srcId,dstId,deaths);                     eHandleSecondary(srcId,dstId,msgTime)}

  }

  def vHandle(srcID : Int,msgTime:Long) : Unit = {
    EntityStorage.timings(msgTime)
    EntityStorage.messageCount.incrementAndGet()
  }

  def vHandleSecondary(srcID : Int,msgTime:Long) : Unit = {
    EntityStorage.timings(msgTime)
    EntityStorage.secondaryMessageCount.incrementAndGet()
  }
  def eHandle(srcID : Int, dstID : Int,msgTime:Long) : Unit = {
    EntityStorage.timings(msgTime)
    EntityStorage.messageCount.incrementAndGet()
  }

  def eHandleSecondary(srcID : Int, dstID : Int,msgTime:Long) : Unit = {
    EntityStorage.timings(msgTime)
    EntityStorage.messageCount.incrementAndGet()
  }

}
