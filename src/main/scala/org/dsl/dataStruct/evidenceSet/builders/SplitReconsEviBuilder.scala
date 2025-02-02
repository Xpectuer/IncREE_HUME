package org.dsl.dataStruct.evidenceSet.builders

import org.dsl.dataStruct.Interval
import org.dsl.dataStruct.evidenceSet.{HPEvidenceSet, IEvidenceSet}
import org.dsl.pb.ProgressBar
import org.dsl.utils.{Config, HUMELogger, Wrappers}
import org.dsl.utils.TPID.{TPID, TPIDInterval}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.parallel

object SplitReconsEviBuilder {

  val logger: HUMELogger = HUMELogger(getClass.getName)


  def unfoldTasks(taskSize: TPID, chunkSize: Int): Iterator[TPIDInterval] = {
    val steps = Iterator.iterate(Interval(0L, chunkSize))(i => Interval(i.begin + chunkSize, i.end + chunkSize)).takeWhile(_.begin <= taskSize)
    if (taskSize % chunkSize == 0) steps else steps ++ Iterator.single(Interval(chunkSize * (taskSize / chunkSize), taskSize))
  }

  private def chunkSize(taskSize: Int, nproc: Int) = {
    // 1000 tasks for each processor
    taskSize / (nproc * 100)
  }

//  val FRAG_SIZE = 5000
//  private val CHUNK_SIZE = 5000 * 1000

  val FRAG_SIZE = Config.FRAG_SIZE
    private val CHUNK_SIZE = Config.CHUNK_SIZE
  //  val FRAG_SIZE = 500
  //  private val CHUNK_SIZE = 5000

  def buildFullEvi(evb: EvidenceSetBuilder, size: Int): IEvidenceSet = {
    val numPartialEviSets = Config.nproc * 4
    val taskSize = size.toLong * size.toLong

    if (taskSize >= CHUNK_SIZE) {
      val chunkSize = CHUNK_SIZE


      val chunkList = unfoldTasks(taskSize, chunkSize)
      val chunkLists = chunkList.grouped(numPartialEviSets)
      val fullEvi = HPEvidenceSet()
      val numPhases = (taskSize / (chunkSize * numPartialEviSets)).toInt
      val pb = new ProgressBar(numPhases)

      logger.debug("chunkList:", chunkLists)
      chunkLists.foreach(
        chunkForThreads =>
          {
            val partialEvis = new ConcurrentLinkedQueue[IEvidenceSet]()
            chunkForThreads.par.foreach(
              //            chunkForThreads.foreach (
              interval => {
                //logger.info(s"Interval ${interval}")
                val begin = interval.begin
                val end = interval.end

                val partialEvi = evb.buildPartialEvi(begin, end)
                partialEvis.add(partialEvi)
              })


            // Merge
            val r = partialEvis.forEach(partialEvi =>

              for ((predSet, count) <- partialEvi) {
                fullEvi.add(predSet, count)
              })

            pb += 1
            r
          })


      fullEvi

    } else {
      // single thread
      // todo: correctness
      val eviset = evb.buildPartialEvi(0, taskSize)
      logger.debug(s"Evidence Set $eviset")
      eviset
    }

  }
}
