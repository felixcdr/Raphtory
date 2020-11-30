package com.raphtory.examples.wordSemantic.routers

import com.raphtory.core.components.Router.RouterWorker
import com.raphtory.core.model.communication._

import scala.collection.mutable.ListBuffer
import scala.collection.parallel.mutable.ParHashSet

class CooccurrenceMatrixRouterFiltered(override val routerId: Int, override val workerID:Int, override val initialManagerCount: Int, override val initialRouterCount: Int)
  extends RouterWorker[StringSpoutGoing](routerId,workerID, initialManagerCount, initialRouterCount) {
  val THR = System.getenv().getOrDefault("COOC_FREQ_THRESHOLD ", "0.0").trim.toDouble

  override protected def parseTuple(tuple: StringSpoutGoing): ParHashSet[GraphUpdate] = {
    //println(record)
    var dp =tuple.value.split(" ").map(_.trim)
    val occurenceTime = dp.head.toLong//DateFormatting(dp.head) //.slice(4, dp.head.length)
    val scale = dp(1).toDouble
    val commands = new ParHashSet[GraphUpdate]()
    try {
      dp = dp.last.split("\t")
      val srcClusterId = assignID(dp.head)
      commands+= (
        VertexAddWithProperties(
          msgTime = occurenceTime,
          srcID = srcClusterId,
          Properties(StringProperty("Word", dp.head))
        )
      )
      val len = dp.length
      for (i <- 1 until len by 2) {
        if ((dp(i+1).toLong/scale) >= THR) {
          val dstClusterId = assignID(dp(i))
          val coocWeight = dp(i + 1).toLong


        commands+= (
            VertexAddWithProperties(
              msgTime = occurenceTime,
              srcID = dstClusterId,
              Properties(StringProperty("Word", dp(i)))
            )
          )

          commands+= (
            EdgeAddWithProperties(
              msgTime = occurenceTime,
              srcID = srcClusterId,
              dstID = dstClusterId,
              Properties(LongProperty("Frequency", coocWeight),
                  DoubleProperty("ScaledFreq", coocWeight/scale))
            )
          )
        }
      }
    }catch {
      case e: Exception => println(e, dp.length, tuple.asInstanceOf[String])
    }
    commands
  }
}