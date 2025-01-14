package com.raphtory.core.actors.orchestration.clustermanager

import java.util.concurrent.atomic.AtomicLong

import akka.actor.ActorRef
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import com.raphtory.core.actors.RaphtoryActor
import com.raphtory.core.actors.orchestration.clustermanager.WatermarkManager.Message.{ProbeWatermark, WatermarkTime, WhatsTheTime}
import kamon.Kamon

import java.time.LocalDateTime
import scala.collection.parallel.mutable.ParTrieMap
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class WatermarkManager(managerCount: Int) extends RaphtoryActor  {
  implicit val executionContext: ExecutionContext = context.system.dispatcher
  val workerPaths = for {
    i <- 0 until managerCount
    j <- 0 until totalWorkers
  } yield s"/user/Manager_${i}_child_$j"

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(delay = 60.seconds, receiver = self, message = "probe")
  }
  val safeTime = Kamon.gauge("Raphtory_Safe_Time").withTag("actor",s"WatermarkManager")
  val safeTimestamp:AtomicLong = new AtomicLong(0)

  private val safeMessageMap = ParTrieMap[String, Long]()
  var counter = 0;

  val mediator: ActorRef = DistributedPubSub(context.system).mediator
  mediator ! DistributedPubSubMediator.Put(self)

  override def receive: Receive = {
    case "probe" => probeWatermark()
    case u:WatermarkTime => processWatermarkTime(u)
    case WhatsTheTime => sender() ! WatermarkTime(safeTimestamp.get())
  }

  def probeWatermark() = {
    workerPaths.foreach { workerPath =>
      mediator ! new DistributedPubSubMediator.Send(
        workerPath,
        ProbeWatermark
      )
    }
  }

  def processWatermarkTime(u:WatermarkTime):Unit = {
    safeMessageMap put(sender().path.toString,u.time)
    counter +=1
    if(counter%(totalWorkers*managerCount)==0) {
      val watermark = safeMessageMap.map(x=>x._2).min
      safeTime.update(watermark)
      safeTimestamp.set(watermark)
      val max = safeMessageMap.maxBy(x=> x._2)
      val min = safeMessageMap.minBy(x=> x._2)
      println(s" ${ LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))} . Minimum Watermark: ${min._1} ${min._2} Maximum Watermark: ${max._1} ${max._2}")
      context.system.scheduler.scheduleOnce(delay = 10.seconds, receiver = self, message = "probe")
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