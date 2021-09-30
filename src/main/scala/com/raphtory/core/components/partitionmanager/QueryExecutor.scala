package com.raphtory.core.components.partitionmanager

import akka.actor.ActorRef
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import com.raphtory.core.components.RaphtoryActor
import com.raphtory.core.components.partitionmanager.QueryExecutor.State
import com.raphtory.core.components.querymanager.QueryHandler.Message.{CreatePerspective, ExecutorEstablished, MessagesReceived, PerspectiveEstablished}
import com.raphtory.core.implementations
import com.raphtory.core.implementations.objectgraph.ObjectGraphLens
import com.raphtory.core.implementations.objectgraph.messaging.VertexMessageHandler
import com.raphtory.core.model.algorithm.{Iterate, Select, Step, VertexFilter}
import com.raphtory.core.model.graph.GraphPartition
import com.raphtory.core.model.graph.visitor.Vertex

case class QueryExecutor(partition: Int, storage: GraphPartition, jobId: String, handlerRef:ActorRef) extends RaphtoryActor {

  override def preStart(): Unit = {
    log.debug(s"Query Executor ${self.path} for Job [$jobId] belonging to Reader [$partition] started.")
    handlerRef ! ExecutorEstablished(partition, self)
  }

  override def receive: Receive = work(State(null,0, 0))

  private def work(state: State): Receive = {
    case CreatePerspective(neighbours, timestamp, window) =>
      context.become(work(state.copy(
        graphLens = ObjectGraphLens(jobId, timestamp, window, 0, storage, VertexMessageHandler(neighbours)),
        sentMessageCount = 0,
        receivedMessageCount = 0)
      ))
      sender ! PerspectiveEstablished
    case Step(f)               => {
      println(s"Partition $partition have been asked to do a Step operation. Not sure How yet :'( ")
      sender() ! MessagesReceived(0,0)
    }
    case Iterate(f,iterations) => {
      println(s"Partition $partition have been asked to do an Iterate operation. Not sure How yet :'( ")
      sender() ! MessagesReceived(0,0)
    }
    case VertexFilter(f)       => {
      println(s"Partition $partition have been asked to do a Filter operation. Not sure How yet :'( ")
      sender() ! MessagesReceived(0,0)
    }
    case Select(f)             => {
      println(s"Partition $partition have been asked to do a Select operation. Not sure How yet :'( ")
      sender() ! MessagesReceived(0,0)
    }
   }




}

object QueryExecutor {
  private case class State(graphLens: ObjectGraphLens,sentMessageCount: Int, receivedMessageCount: Int) {
    def updateReceivedMessageCount(f: Int => Int): State = copy(receivedMessageCount = f(receivedMessageCount))
  }
}