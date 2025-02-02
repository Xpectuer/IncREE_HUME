package org.dsl.mining

import org.dsl.utils.Config

class BatchMiningOps extends BFSOps[LevelArg[Double], ResArg[Double], CollectionArg[Double]] {

  private def untilDeepest(level: Int) = {
    level <= Config.levelUpperBound
  }

  private def feasible(oldNode: State, newNode: State) = {
    oldNode.conf > Config.confLowerBound && newNode.conf > Config.confLowerBound
  }


  def process(vertex: LevelArg[Double]): ResArg[Double] = {
    vertex match {
      case LevelArg(oldS, newS, _, _, fullEviSize, result, supp_threshold, conf_threshold)
      =>

        // todo: judge condition
        // open level

        (oldS, newS) match {
          case (
            o@State(oldPred, _, oldSuppCnt, oldConf, levelOld),
            n@State(newPred, _, newSuppCnt, newConf, levelNew)) if untilDeepest(levelOld) =>

            val isMin = REEMiner.isMinimal(result.keys.toList, oldPred)
            val oldSupp = oldSuppCnt.toDouble / fullEviSize.toDouble
            val newSupp = newSuppCnt.toDouble / fullEviSize.toDouble


            // find minimal rule
            if (oldConf >= conf_threshold && oldSupp >= supp_threshold) {
              if (isMin) {
                // add to result
                ResArg(None, Some((oldPred, oldConf)), Some(newPred, newConf))
              } else {
                // not minimal
                ResArg(None, None, Some(oldPred, oldConf))
              }
            } else if (oldConf < conf_threshold) {
              if (oldSupp < supp_threshold) {

                ResArg(None, None, Some(oldPred, oldConf))
              } else {
                if (newSupp >= supp_threshold) {

                  ResArg(Some(newS), None, None)
                } else {

                  ResArg(None, None, Some(newPred, newConf))
                  //                  ResArg(None, None, Some(oldPred, oldConf))
                }
              }

            } else {
              // oldSupp < supp_threshold
              //              if (oldSupp > 0) {
              //
              //                ResArg(None, None, Some(oldPred, oldConf))
              //              } else {
              //
              //                ResArg(None, None, None)
              //              }
              ResArg(None, None, Some(oldPred, oldConf))


            }

          case _ => ResArg(None, None, None)
        }


      case _ => ResArg(None, None, None)
    }

  }


  override def merge(cc: CollectionArg[Double], r: ResArg[Double]): CollectionArg[Double] = { // todo:
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
      case _ => ???
    }

    cc
  }


  override def mergeAll(in: CollectionArg[Double], out: CollectionArg[Double]): CollectionArg[Double] = {
    out.resultLevel.++=(in.resultLevel)
    out.prunedLevel.++=(in.prunedLevel)
    out.nextLevel.++=(in.nextLevel)
    out
  }
}





