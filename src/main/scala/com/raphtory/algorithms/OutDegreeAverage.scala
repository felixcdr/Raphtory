package com.raphtory.algorithms

import com.raphtory.core.analysis.api.Analyser

import scala.collection.mutable.ArrayBuffer

class OutDegreeAverage(args:Array[String]) extends Analyser[Any](args){

  override def analyse(): Unit = {}
  override def setup(): Unit   = {}
  override def returnResults(): Any = {
    val outedges = view.getVertices().map { vertex =>vertex.getOutEdges.size}.filter(x=>x>0)
    val degree = outedges.sum
    val totalV   = outedges.size
    (totalV, degree)
  }

  override def defineMaxSteps(): Int = 1

  override def extractResults(results: List[Any]): Map[String,Any]  = {
    val endResults  = results.asInstanceOf[ArrayBuffer[(Int, Int)]]

    val totalVert   = endResults.map(x => x._1).sum
    val totalEdge   = endResults.map(x => x._2).sum

    val degree =
      try totalEdge.toDouble / totalVert.toDouble
      catch { case _: ArithmeticException => 0 }

    Map("vertices"->totalVert,"edges"->totalEdge,"degree"->degree)
  }

}
