package com.raphtory.core.components.orchestration.raphtoryleader

import java.util.concurrent.atomic.AtomicLong
import akka.actor.ActorRef
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import com.raphtory.core.components.RaphtoryActor
import com.raphtory.core.components.RaphtoryActor.totalPartitions
import com.raphtory.core.components.orchestration.raphtoryleader.WatermarkManager.Message.{ProbeWatermark, WatermarkTime, WhatsTheTime}
import com.raphtory.core.components.querymanager.QueryHandler.Message.{TimeCheck, TimeResponse}

import java.time.LocalDateTime
import scala.collection.parallel.mutable.ParTrieMap
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class WatermarkManager extends RaphtoryActor  {

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(delay = 10.seconds, receiver = self, message = "probe")
  }
  var safeTimestamp:Long = 0L

  private val safeMessageMap = ParTrieMap[String, Long]()
  var counter = 0;

  override def receive: Receive = {
    case "probe"         => probeWatermark()
    case u:WatermarkTime => processWatermarkTime(u)
    case WhatsTheTime    =>
      val time = safeTimestamp
      sender() ! WatermarkTime(time)
  }

  def probeWatermark() = {
    getAllWriters().foreach { workerPath =>
      mediator ! new DistributedPubSubMediator.Send(
        workerPath,
        ProbeWatermark
      )
    }
  }

  def processWatermarkTime(u:WatermarkTime):Unit = {
    safeMessageMap put(sender().path.toString,u.time)
    counter +=1
    if(counter==totalPartitions) {
      val max = safeMessageMap.maxBy(x=> x._2)
      val min = safeMessageMap.minBy(x=> x._2)
      safeTimestamp = min._2
      log.info(s"Minimum Watermark: ${min._1} ${min._2} Maximum Watermark: ${max._1} ${max._2}")
      context.system.scheduler.scheduleOnce(delay = 10.seconds, receiver = self, message = "probe")
      counter=0
    }
  }
}

object WatermarkManager {
  object Message {
    case object ProbeWatermark
    case class WatermarkTime(time:Long)
    case object SaveState
    case object WhatsTheTime
  }
}