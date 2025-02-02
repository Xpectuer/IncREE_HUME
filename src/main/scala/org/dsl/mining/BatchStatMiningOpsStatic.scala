package org.dsl.mining

import org.dsl.mining.PredSpace.logger
import org.dsl.utils.Config

object BatchStatMiningOpsStatic {


  private def untilDeepest(level: Int) = {
    level <= Config.levelUpperBound
  }

  private def feasible(oldNode: State, newNode: State) = {
    oldNode.conf > Config.confLowerBound && newNode.conf > Config.confLowerBound
  }


  def process(vertex: LevelArg[Stat]): ResArg[Stat] = {
    vertex match {
      case LevelArg(oldS, newS, _, _, fullEviSize, _, supp_threshold, conf_threshold)
      =>
        // open level
        (oldS, newS) match {
          case (
            State(oldPred, _, old_supp_cnt, old_conf, levelOld),
            State(newPred, _, new_supp_cnt, new_conf, levelNew)) if untilDeepest(levelOld) =>

            val supp_threshold_cnt = (fullEviSize * supp_threshold).toLong
            val min_supp_threshold_cnt = (fullEviSize * Config.MIN_SUPP).toLong
            val min_conf = Config.MIN_CONF

            val oldStat = Stat(old_supp_cnt, old_conf)
            val newStat = Stat(new_supp_cnt, new_conf)

            // find minimal rule
            if (old_conf >= conf_threshold && old_supp_cnt >= supp_threshold_cnt) {

                // add to result
                ResArg(None, Some((oldPred, oldStat)), None)

            } else if (old_conf < conf_threshold) {

                // (0) -> p0 where should it go?
              if (old_supp_cnt > min_supp_threshold_cnt && old_supp_cnt < supp_threshold_cnt) {

                ResArg(None, None, Some(oldPred, oldStat))

              } else { // oldsupp >= \sigma

                if (new_supp_cnt >= supp_threshold_cnt) {

                  if(Config.ENABLE_MIN_CONF_FILTER_OPT && new_conf < min_conf && old_conf < min_conf)
                    ResArg(None, None, Some(newPred, newStat))
                  else // pruned double low conf
                    ResArg(Some(newS), None, None)

                } else if(new_supp_cnt >= min_supp_threshold_cnt) { // newSupp < \sigma
                  ResArg(None, None, Some(newPred, newStat))
                } else {
                  ResArg(None, None, None)
                }
              }

            } else { // supp < \sigma && conf > \delta
              if (new_supp_cnt > min_supp_threshold_cnt) {
                // newSupp < \sigma
                ResArg(None, None, Some(oldPred, oldStat))
              } else {
                ResArg(None, None, None)
              }

            }

          case _ => ResArg(None, None, None)
        }


      case _ => ResArg(None, None, None)
    }

  }


  def merge(cc: CollectionArg[Stat], r: ResArg[Stat]): CollectionArg[Stat] = {
    // todo:
    //    val K = 3
    r match {
      // todo: abstract merge policy result [min, max]
      case ResArg(None, Some(r), None) => cc.resultLevel.update(r._1, r._2)
      case ResArg(Some(l), None, None) => cc.nextLevel.update(l, ())
      case ResArg(None, None, Some(p)) => cc.prunedLevel.update(p._1, p._2)
      case ResArg(None, None, None) => ()
      case ResArg(None, Some(r), Some(p)) =>
        cc.resultLevel.update(r._1, r._2)
        cc.prunedLevel.update(p._1, p._2)
      case _ => logger.fatal(s"Failed With ${r}")
    }

    cc
  }


  def mergeAll(a: CollectionArg[Stat], b: CollectionArg[Stat]): CollectionArg[Stat] = {
    a.resultLevel++= b.resultLevel
    a.prunedLevel++= b.prunedLevel
    a.nextLevel++= b.nextLevel
    a
  }
}



