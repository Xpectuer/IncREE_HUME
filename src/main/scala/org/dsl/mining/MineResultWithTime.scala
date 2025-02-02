package org.dsl.mining

import org.dsl.mining.MineResultWithTime.{KEY_MINE_RESULT, KEY_SAMPLE_TIME, KEY_TIME}
import org.dsl.reasoning.predicate.PredicateIndexProviderBuilder.PredicateIndexProvider

case class MineResultWithTime(mineResult: MineResult, totalTime: Double, sampleTime: Double) {

  def withTime(timeNew: Double): MineResultWithTime = {
    MineResultWithTime(mineResult, timeNew, sampleTime)
  }
  def toJSON: String = {
    import upickle.default._

    implicit val rw: Writer[MineResultWithTime] =
      writer[ujson.Value].comap {
        case MineResultWithTime(ms, time, stime) =>
          ujson.Obj(KEY_MINE_RESULT -> ms.toJSON, KEY_TIME -> time, KEY_SAMPLE_TIME -> stime)
      }

    write(this)
  }
}

object MineResultWithTime {

  val KEY_MINE_RESULT = "mine_result"
  val KEY_TIME = "time"
  val KEY_SAMPLE_TIME = "stime"

  def fromJSON(j: String, p2i: PredicateIndexProvider): MineResultWithTime = {
    import upickle.default._

    implicit val rw: Reader[MineResultWithTime] =
      reader[ujson.Value].map(
        json => {
          val ms = MineResult.fromJSON(json(KEY_MINE_RESULT).str, p2i)
          val time = json(KEY_TIME).num
          val stime = json(KEY_SAMPLE_TIME).num

          MineResultWithTime(ms, time, stime)

        }
      )

    read[MineResultWithTime](j)
  }

}
