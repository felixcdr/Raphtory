package com.raphtory.spouts

import java.io.{BufferedReader, File, FileInputStream, FileReader, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

import com.raphtory.core.components.Spout.SpoutTrait
import com.raphtory.core.components.Spout.SpoutTrait.DomainMessage
import com.raphtory.core.model.communication.StringSpoutGoing
import com.raphtory.spouts.FileSpout.Message.FileDomain
import com.raphtory.spouts.FileSpout.Message.Increase
import com.raphtory.spouts.FileSpout.Message.NextFile
import com.raphtory.spouts.FileSpout.Message.NextLineBlock
import com.typesafe.scalalogging.LazyLogging

import scala.annotation.tailrec
import scala.concurrent.duration._

final case class FileSpout() extends SpoutTrait[FileDomain, StringSpoutGoing] {
  log.info("initialise FileSpout")
  private val directory  = System.getenv().getOrDefault("FILE_SPOUT_DIRECTORY", "/app").trim
  private val fileName   = System.getenv().getOrDefault("FILE_SPOUT_FILENAME", "").trim //gabNetwork500.csv
  private val dropHeader = System.getenv().getOrDefault("FILE_SPOUT_DROP_HEADER", "false").trim.toBoolean
  private val JUMP       = System.getenv().getOrDefault("FILE_SPOUT_BLOCK_SIZE", "10").trim.toInt
  private val INCREMENT  = System.getenv().getOrDefault("FILE_SPOUT_INCREMENT", "0").trim.toInt
  private val TIME       = System.getenv().getOrDefault("FILE_SPOUT_TIME", "60").trim.toInt

  private var fileManager = FileManager(directory, fileName, dropHeader, JUMP)
  val t0 = System.nanoTime()

  def startSpout(): Unit = {
    self ! NextLineBlock
    context.system.scheduler.scheduleOnce(TIME.seconds, self, Increase)
  }

  def handleDomainMessage(message: FileDomain): Unit = message match {
    case Increase =>
      if (fileManager.allCompleted) {
        println("All files read1-" + (System.nanoTime() - t0))
        dataFinished()
      }
      else {
        fileManager = fileManager.increaseBlockSize(INCREMENT)
        context.system.scheduler.scheduleOnce(TIME.seconds, self, Increase)
      }

    case NextLineBlock =>
      if (fileManager.allCompleted)
        println("All files read2-" + (System.nanoTime() - t0))
      else {
        val (newFileManager, block) = fileManager.nextLineBlock()
        fileManager = newFileManager
        block.foreach(str => sendTuple(StringSpoutGoing(str)))
        self ! NextLineBlock
      }
    case NextFile =>
      if (fileManager.allCompleted)
        println("All files read3-" + (System.nanoTime() - t0))
      else {
        fileManager = fileManager.nextFile()
        self ! NextLineBlock
      }
  }
}

object FileSpout {
  object Message {
    sealed trait FileDomain extends DomainMessage
    case object Increase      extends FileDomain
    case object NextLineBlock extends FileDomain
    case object NextFile      extends FileDomain
  }
}

final case class FileManager private (
    currentFileReader: Option[BufferedReader],
    restFiles: List[File],
    dropHeader: Boolean,
    blockSize: Int
) extends LazyLogging {
  def nextFile(): FileManager = this.copy(currentFileReader = None)

  lazy val allCompleted: Boolean = currentFileReader.isEmpty && restFiles.isEmpty

  def increaseBlockSize(inc: Int): FileManager = this.copy(blockSize = blockSize + inc)

  def nextLineBlock(): (FileManager, List[String]) = currentFileReader match {
    case None =>
      restFiles match {
        case Nil => (this, List.empty)
        case head :: tail =>
          val reader             = getFileReader(head)
          val (block, endOfFile) = readBlockAndIsEnd(reader)
          val currentReader      = if (endOfFile) None else Some(reader)
          (this.copy(currentFileReader = currentReader, restFiles = tail), block)
      }
    case Some(reader) =>
      val (block, endOfFile) = readBlockAndIsEnd(reader)
      if (endOfFile) (this.copy(currentFileReader = None), block)
      else (this, block)

  }

  private def readBlockAndIsEnd(reader: BufferedReader): (List[String], Boolean) = {
    @tailrec
    def rec(count: Int, result: List[String]): (List[String], Boolean) =
      if (count > 0) {
        val line = reader.readLine()
        if (line != null)
          rec(count - 1, result :+ line)
        else {
          reader.close()
          (result, true)
        }
      } else (result, false)
    rec(blockSize, List.empty)
  }

  private def getFileReader(file: File): BufferedReader = {
    logger.info(s"Reading file ${file.getCanonicalPath}")

    var br = new BufferedReader(new FileReader(file))
    if (file.getName.endsWith(".gz")) {
      val inStream = new FileInputStream(file)
      val inGzipStream = new GZIPInputStream(inStream)
      val inReader = new InputStreamReader(inGzipStream) //default to UTF-8
      br = new BufferedReader(inReader)
    }
    if (dropHeader) {
      br.readLine()
    }
    br
  }
}

object FileManager extends LazyLogging {
  private val joiner     = System.getenv().getOrDefault("FILE_SPOUT_JOINER", "/").trim //gabNetwork500.csv
  def apply(dir: String, fileName: String, dropHeader: Boolean, blockSize: Int): FileManager = {
    val filesToRead =
      if (fileName.isEmpty)
        getListOfFiles(dir)
      else {
        val file = new File(dir + joiner + fileName)
        if (file.exists && file.isFile)
          List(file)
        else {
          logger.error(s"File $dir$joiner$fileName does not exist or is not file ")
          List.empty
        }
      }
    FileManager(None, filesToRead, dropHeader, blockSize)
  }

  private def getListOfFiles(dir: String): List[File] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory)
      d.listFiles.toList.filter(f => f.isFile && !f.isHidden)
    else {
      logger.error(s"Directory $dir does not exist or is not directory")
      List.empty
    }
  }
}
