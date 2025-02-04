package com.raphtory.core.model.entities

import com.raphtory.core.actors.partitionmanager.workers.ParquetVertex
import com.raphtory.core.analysis.GraphLens
import com.raphtory.core.analysis.entity.{Edge, Vertex}
import com.raphtory.core.model.EntityStorage

import scala.collection.mutable
import scala.collection.parallel.mutable.ParTrieMap

/** Companion Vertex object (extended creator for storage loads) */
object RaphtoryVertex {
  def apply(
      creationTime: Long,
      vertexId: Int,
      previousState: mutable.TreeMap[Long, Boolean],
      properties: ParTrieMap[String, Property],
      storage: EntityStorage
  ) = {
    val v = new RaphtoryVertex(creationTime, vertexId, initialValue = true)
    v.history = previousState
    //v.associatedEdges = associatedEdges
    v.properties = properties
    v
  }

  def apply(parquet: ParquetVertex):RaphtoryVertex = {
    val vertex = new RaphtoryVertex(parquet.history.head._1,parquet.id,parquet.history.head._2)
    parquet.history.foreach(update=> if(update._2) vertex.revive(update._1) else vertex.kill(update._1))
    parquet.properties.foreach(prop=> vertex.properties +=((prop.key,Property(prop))))
    parquet.incoming.foreach(edge=> vertex.incomingEdges+=((edge.src,RaphtoryEdge(edge))))
    parquet.outgoing.foreach(edge=> vertex.outgoingEdges+=((edge.dst,RaphtoryEdge(edge))))
    vertex
  }

}

class RaphtoryVertex(msgTime: Long, val vertexId: Long, initialValue: Boolean)
        extends RaphtoryEntity(msgTime, initialValue) {

  var incomingEdges = ParTrieMap[Long, RaphtoryEdge]() //Map of all edges associated with the vertex
  var outgoingEdges = ParTrieMap[Long, RaphtoryEdge]()

  private var edgesRequiringSync = 0

  //Functions for adding associated edges to this vertex
  def incrementEdgesRequiringSync()  =edgesRequiringSync+=1
  def getEdgesRequringSync() = edgesRequiringSync
  def addIncomingEdge(edge: RaphtoryEdge): Unit = incomingEdges.put(edge.getSrcId, edge)
  def addOutgoingEdge(edge: RaphtoryEdge): Unit = outgoingEdges.put(edge.getDstId, edge)
  def addAssociatedEdge(edge: RaphtoryEdge): Unit =
    if (edge.getSrcId == vertexId) addOutgoingEdge(edge) else addIncomingEdge(edge)
  def getOutgoingEdge(id: Long): Option[RaphtoryEdge] = outgoingEdges.get(id)
  def getIncomingEdge(id: Long): Option[RaphtoryEdge] = incomingEdges.get(id)

  def viewAt(time: Long,lens:GraphLens): Vertex = {
    Vertex(this,
      incomingEdges.collect {
        case (k, edge) if edge.aliveAt(time) =>
          k -> Edge(edge, k, lens)
      },
      outgoingEdges.collect {
        case (k, edge) if edge.aliveAt(time) =>
          k -> Edge(edge, k, lens)
      },
      lens)
  }

  def viewAtWithWindow(time: Long, windowSize: Long,lens:GraphLens): Vertex = {
    Vertex(this,
      incomingEdges.collect {
        case (k, edge) if edge.aliveAtWithWindow(time,windowSize) =>
          k -> Edge(edge, k, lens)
      },
      outgoingEdges.collect {
        case (k, edge) if edge.aliveAtWithWindow(time,windowSize) =>
          k -> Edge(edge, k, lens)
      },
      lens)
  }

  def serialise(): ParquetVertex = ParquetVertex(vertexId,history.toList,properties.map(x=> x._2.serialise(x._1)).toList,incomingEdges.map(x=>x._2.serialise()).toList,outgoingEdges.map(x=>x._2.serialise()).toList)


}
