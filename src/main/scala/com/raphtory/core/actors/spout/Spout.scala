package com.raphtory.core.actors.spout


trait Spout[+T]{
  private var dataComplete = false

  def setupDataSource():Unit

  def generateData():Option[T]

  def closeDataSource():Unit

  def dataSourceComplete():Unit = dataComplete=true

  def isComplete():Boolean = dataComplete
}
