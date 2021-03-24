package com.raphtory.core.analysis.GraphLenses

import akka.actor.ActorContext
import com.raphtory.core.analysis.api.ManagerCount
import com.raphtory.core.actors.PartitionManager.Workers.ViewJob
import com.raphtory.core.model.EntityStorage
import com.raphtory.core.analysis.entity.Vertex
import com.raphtory.core.model.entities.RaphtoryVertex
import kamon.Kamon

import scala.collection.parallel.ParIterable

class ViewLens(
                jobID: ViewJob,
                superstep: Int,
                workerID: Int,
                storage: EntityStorage,
                managerCount: ManagerCount
) extends GraphLens(jobID, superstep, storage, managerCount) {

  private var keySet: ParIterable[Vertex]          = ParIterable[Vertex]()
  private var keySetMessages: ParIterable[Vertex]  = ParIterable[Vertex]()
  private var messageFilter                               = false
  private var firstRun                                    = true

  private val viewTimer = Kamon.gauge("Raphtory_View_Build_Time")
    .withTag("Partition",storage.managerID)
    .withTag("Worker",workerID)
    .withTag("JobID",jobID.jobID)
    .withTag("timestamp",jobID.timestamp)

  override def getMessagedVertices()(implicit context: ActorContext, managerCount: ManagerCount):  ParIterable[Vertex] = {
    val timetaken = System.currentTimeMillis()
    if (!messageFilter) {
      keySetMessages = storage.vertices.filter {
        case (id: Long, vertex: RaphtoryVertex) =>
          vertex.aliveAt(jobID.timestamp) && vertex.multiQueue.getMessageQueue(jobID, superstep).nonEmpty
      }.map(v =>  new Vertex(v._2.viewAt(jobID.timestamp), jobID, superstep, this))
      messageFilter = true
    }
    viewTimer.update(System.currentTimeMillis()-timetaken)
    keySetMessages
  }

  override def getVertices()(implicit context: ActorContext, managerCount: ManagerCount): ParIterable[Vertex] = {
    if (firstRun) {
      val timetaken = System.currentTimeMillis()
      keySet = storage.vertices.filter(v => v._2.aliveAt(jobID.timestamp)).map(v =>  new Vertex(v._2.viewAt(jobID.timestamp), jobID, superstep, this))
      firstRun = false
      viewTimer.update(System.currentTimeMillis()-timetaken)
    }
    keySet
  }
  //override def getVertex(id : Long)(implicit context : ActorContext, managerCount : ManagerCount) : VertexVisitor = new VertexVisitor(keySet(id.toInt).viewAt(timestamp),job(),superstep,this,timestamp,-1)


  override def checkVotes(workerID: Int): Boolean =
//    println(workerID +" "+ messageFilter)
    if (messageFilter)
      keySetMessages.size == voteCount.get
    else
      keySet.size == voteCount.get
}