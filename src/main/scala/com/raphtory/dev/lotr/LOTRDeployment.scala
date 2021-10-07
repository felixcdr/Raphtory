package com.raphtory.dev.lotr

import com.raphtory.algorithms.{ConnectedComponents, StateTest}
import com.raphtory.core.build.server.RaphtoryPD
import com.raphtory.dev.lotr.LOTRClient.{algo, client}
import com.raphtory.serialisers.{DefaultSerialiser, MongoSerialiser}

object LOTRDeployment extends App{
  val source  = new LOTRSpout()
  val builder = new LOTRGraphBuilder()
  val rg = RaphtoryPD[String](source,builder)

  val distinctivenessCentrality = new DistinctivenessCentrality()
  rg.pointQuery(distinctivenessCentrality,10000)
}
