package com.raphtory.core.actors.orchestration.clustermanager

import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import com.raphtory.core.actors.RaphtoryActor

class SeedActor() extends RaphtoryActor {
  val cluster: Cluster = Cluster(context.system)

  override def preStart(): Unit = {
    log.debug("SeedActor is being started.")

    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive: Receive = {

    case evt: MemberUp          => processMemberUpEvent(evt)
    case evt: MemberRemoved     => processMemberRemovedEvent(evt)
    case evt: UnreachableMember => processUnreachableMemberEvent(evt)
    case evt: MemberExited      => processMemberExitedEvent(evt)
    case x                      => log.warning("SeedActor received unknown [{}] message.", x)
  }

  private def processMemberUpEvent(evt: MemberUp): Unit = {
    log.debug(s"SeedActor received [{}] event.", evt)

//    svr.nodes.synchronized {
//      svr.nodes += evt.member
//    }
  }

  private def processMemberRemovedEvent(evt: MemberRemoved): Unit = {
    log.debug(s"SeedActor received [{}] event.", evt)

//    svr.nodes.synchronized {
//      svr.nodes -= evt.member
//    }
  }
  private def processMemberExitedEvent(evt: MemberExited): Unit = {
    log.debug(s"SeedActor received [{}] event.", evt)

//    svr.nodes.synchronized {
//      svr.nodes -= evt.member
//    }
  }

  private def processUnreachableMemberEvent(evt: UnreachableMember): Unit = {
    log.debug(s"SeedActor received [{}] event.", evt)

    log.warning("processUnreachableMemberEvent in SeedActor has not been implemented. Ignoring request.")
  }
}
