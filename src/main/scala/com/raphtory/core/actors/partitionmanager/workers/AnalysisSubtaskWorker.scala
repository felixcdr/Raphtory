package com.raphtory.core.actors.partitionmanager.workers

import akka.actor.{ActorRef, PoisonPill}
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import com.raphtory.core.actors.analysismanager.AnalysisManager.Message.{JobFailed, KillTask}
import com.raphtory.core.actors.analysismanager.tasks.AnalysisTask.Message._
import com.raphtory.core.actors.orchestration.componentconnector.UpdatedCounter
import com.raphtory.core.actors.partitionmanager.workers.AnalysisSubtaskWorker.State
import com.raphtory.core.actors.RaphtoryActor
import com.raphtory.core.analysis.GraphLens
import com.raphtory.core.analysis.api.Analyser
import com.raphtory.core.model.EntityStorage
import com.raphtory.core.model.communication.VertexMessage
import com.raphtory.core.model.communication.VertexMessageHandler
import kamon.Kamon

import scala.collection.mutable
import scala.util.Failure
import scala.util.Success
import scala.util.Try

final case class AnalysisSubtaskWorker(
    initManagerCount: Int,
    managerId: Int,
    workerId: Int,
    storage: EntityStorage,
    analyzer: Analyser[Any],
    jobId: String,
    taskManager:ActorRef
) extends RaphtoryActor {

  private val mediator: ActorRef = DistributedPubSub(context.system).mediator
  mediator ! DistributedPubSubMediator.Put(self)


  override def preStart(): Unit = {
    log.debug(
      s"AnalysisSubtaskWorker ${self.path} for Job [$jobId] belonging to ReaderWorker [$workerId] Reader [$managerId] is being started."
    )
    taskManager ! AnalyserPresent((managerId,workerId),self)
  }

  override def postStop(): Unit = log.info(s"Worker $workerId for $jobId Killed")

  override def receive: Receive = work(State(0, 0, initManagerCount))

  private def work(state: State): Receive = {
    case SetupSubtask(neighbours, timestamp, window) =>
      log.debug(s"Job [$jobId] belonging to ReaderWorker [$workerId] Reader [$managerId] is SetupTaskWorker.")
      val initStep       = 0
      val messageHandler = new VertexMessageHandler(neighbours, state.managerCount,jobId)
      val graphLens      = GraphLens(jobId, timestamp, window, initStep, workerId, storage, messageHandler)
      analyzer.sysSetup(graphLens, messageHandler, workerId)
      context.become(work(state.copy(sentMessageCount = 0, receivedMessageCount = 0)))
      sender ! SetupSubtaskDone

    case _: StartSubtask =>
      log.debug(s"Job [$jobId] belonging to ReaderWorker [$workerId] Reader [$managerId] is StartSubtask.")
      val beforeTime = System.currentTimeMillis()
      analyzer.setup()
      val messagesSent = analyzer.messageHandler.getCountandReset()
      sender ! Ready(messagesSent)
      context.become(work(state.copy(sentMessageCount = messagesSent)))
      stepMetric(analyzer).update(System.currentTimeMillis() - beforeTime)

    case _: CheckMessages =>
      log.debug(s"Job [$jobId] belonging to ReaderWorker [$workerId] Reader [$managerId] receives CheckMessages.")
      Kamon
        .gauge("Raphtory_Analysis_Messages_Received")
        .withTag("actor", s"Reader_$managerId")
        .withTag("ID", workerId)
        .withTag("jobID", jobId)
        .withTag("Timestamp", analyzer.view.timestamp)
        .withTag("Superstep", analyzer.view.superStep)
        .update(state.receivedMessageCount)

      Kamon
        .gauge("Raphtory_Analysis_Messages_Sent")
        .withTag("actor", s"Reader_$managerId")
        .withTag("ID", workerId)
        .withTag("ID", workerId)
        .withTag("jobID", jobId)
        .withTag("Timestamp", analyzer.view.timestamp)
        .withTag("Superstep", analyzer.view.superStep)
        .update(state.sentMessageCount)

      sender ! MessagesReceived(state.receivedMessageCount, state.sentMessageCount)

    case _: SetupNextStep =>
      log.debug(s"Job [$jobId] belonging to ReaderWorker [$workerId] Reader [$managerId] receives SetupNextStep.")
      Try(analyzer.view.nextStep()) match {
        case Success(_) =>
          context.become(work(state.copy(sentMessageCount = 0, receivedMessageCount = 0)))
          sender ! SetupNextStepDone
        case Failure(e) => {
          log.error(s"Failed to run setup due to [${e.getStackTrace.mkString("\n")}].")
          sender ! JobFailed
        }
      }


    case _: StartNextStep =>
      log.debug(s"Job [$jobId] belonging to ReaderWorker [$workerId] Reader [$managerId] receives StartNextStep.")
      val beforeTime = System.currentTimeMillis()
      Try(analyzer.analyse()) match {
        case Success(_) =>
          val messageCount = analyzer.messageHandler.getCountandReset()
          sender ! EndStep(analyzer.view.superStep, messageCount, analyzer.view.checkVotes())
          context.become(work(state.copy(sentMessageCount = messageCount)))

        case Failure(e) => {
          log.error(s"Failed to run nextStep due to [${e.getStackTrace.mkString("\n")}].")
          sender ! JobFailed
        }
      }
      stepMetric(analyzer).update(System.currentTimeMillis() - beforeTime)

    case _: Finish =>
      log.debug(s"Job [$jobId] belonging to ReaderWorker [$workerId] Reader [$managerId] receives Finish.")
      Try(analyzer.returnResults()) match {
        case Success(result) => sender ! ReturnResults(result)
        case Failure(e)      => {
          log.error(s"Failed to run nextStep due to [${e.getStackTrace.mkString("\n")}].")
          sender ! JobFailed
        }

      }

    case msg: VertexMessage =>
      log.debug(s"Job [$jobId] belonging to ReaderWorker [$workerId] Reader [$managerId] receives VertexMessage.")
      analyzer.view.receiveMessage(msg)
      context.become(work(state.updateReceivedMessageCount(_ + 1)))

    case UpdatedCounter(newValue) =>
      context.become(work(state.copy(managerCount = newValue)))

    case KillTask(jobID) => self ! PoisonPill

    case unhandled => log.error(s"Unexpected message [$unhandled].")


  }

  private def stepMetric(analyser: Analyser[Any]) =
    Kamon
      .gauge("Raphtory_Superstep_Time")
      .withTag("Partition", storage.managerID)
      .withTag("Worker", workerId)
      .withTag("JobID", jobId)
      .withTag("timestamp", analyzer.view.timestamp)
      .withTag("superstep", analyser.view.superStep)

}

object AnalysisSubtaskWorker {
  private case class State(sentMessageCount: Int, receivedMessageCount: Int, managerCount: Int) {
    def updateReceivedMessageCount(f: Int => Int): State = copy(receivedMessageCount = f(receivedMessageCount))
  }
  object Message {
    case class SetupTaskWorker(timestamp: Long, window: Option[Long])
  }
}
