package com.raphtory.algorithms

import com.raphtory.core.analysis.entity.Vertex

import scala.collection.mutable.ArrayBuffer
import scala.collection.parallel.{ParMap, immutable}
import scala.reflect.io.Path

/**
Description
  Returns outliers detected based on the community structure of the Graph.

  Tha algorithm runs an instance of LPA on the graph, initially, and then defines an outlier score based on a node's
  community membership and how it compares to its neighbors community memberships.

Parameters
  top (Int)       – Defines number of nodes with high outlier score to be returned. (default: 0)
                      If not specified, Raphtory will return the outlier score for all nodes.
  weight (String) - Edge property (default: ""). To be specified in case of weighted graph.
  cutoff (Double) - Outlier score threshold (default: 0.0). Identifies the outliers with an outlier score > cutoff.
  maxIter (Int)   - Maximum iterations for LPA to run. (default: 500)

Returns
  total (Int)     – Number of detected outliers.
  outliers Map(Long, Double) – Map of (node, outlier score) sorted by their outlier score.
                  Returns `top` nodes with outlier score higher than `cutoff` if specified.
**/
object CommunityOutlierDetection {
  def apply(top: Int, weight: String, maxIter: Int, cutoff: Double):
  CommunityOutlierDetection = new CommunityOutlierDetection(Array(top.toString, weight, maxIter.toString,cutoff.toString))
}

class CommunityOutlierDetection(args: Array[String]) extends LPA(args) {
  //args = [top , edge property, maxIter, cutoff]
  val cutoff: Double = if (args.length < 4) 0.0 else args(3).toDouble

  override def doSomething(v: Vertex, neighborLabels: Array[Long]): Unit = {
    val vlabel       = v.getState[Long]("lpalabel")
    val outlierScore = 1 - (neighborLabels.count(_ == vlabel) / neighborLabels.length.toDouble)
    v.setState("outlierscore", outlierScore)
  }

  override def returnResults(): ParMap[Long, List[String]] =
    view
      .getVertices()
      .map(vertex => (vertex.ID(), vertex.getOrSetState[Double]("outlierscore", -1.0)))
      .groupBy(f => f._1)
      .mapValues(f => f.map(_._2.toString).toList)


   override def extractResults(results: List[ParMap[Long, List[String]]]): Map[String,Any]  = {
    val endResults = results.flatten
      .map(x=>(x._1, x._2.head.toFloat))

    val outliers  = endResults.filter(_._2 > cutoff)
    val sorted    = outliers.sortBy(-_._2)
    val sortedstr = sorted.map(x => s""""${x._1}":${x._2}""")
    val top5       = sorted.map(_._1).take(5)
    val total     = outliers.length
    val out       = if (top == 0) sortedstr else sortedstr.take(top)

    Map("total"->total,"top5"->top5,"outliers"->out)
  }
}
