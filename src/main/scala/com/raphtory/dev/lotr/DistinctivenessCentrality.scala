package com.raphtory.dev.lotr

import com.raphtory.core.model.algorithm.{GraphAlgorithm, GraphPerspective, Row}

class DistinctivenessCentrality extends GraphAlgorithm{
  override def algorithm(graph: GraphPerspective): Unit = {
    graph
      .step({ vertex =>
        val neighbours = vertex.getEdges()
        val neighbourIds = neighbours.map(_.ID())

        vertex.setState("neighbours", neighbourIds)

        vertex.messageAllNeighbours(neighbourIds)
      })
      .iterate({
        vertex =>
          val label = vertex.messageQueue[Long].min

          if (label < vertex.getState[Array[Long]]("set")) {
            vertex.setState("cclabel", label)
            vertex messageAllNeighbours label
          }
          else
            vertex.voteToHalt()
      }, 100)
      .select(vertex => Row(Array(vertex.ID(),vertex.getState[Long]("cclabel"))))
      //.filter(r=> r.get(0).asInstanceOf[Long]==18174)
      .writeTo("/Users/bensteer/github/output")
  }
}

