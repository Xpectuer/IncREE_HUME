import org.dsl.emperical.table.TypedColTable
import org.dsl.mining.{PMiners, REE, REEMiner}
import org.dsl.mining.PMiners._allOnePredSet
import org.dsl.pb.ProgressBar
import org.dsl.reasoning.predicate.{Eq, Expression, PredicateSet, TCalc, TypedConstantBin, TypedTupleBin}
import org.dsl.utils.Common._
import org.dsl.utils.Config
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.compat.BitSetFactoryExtensionMethods
import scala.collection.concurrent.TrieMap
import scala.collection.mutable

class PlayGround extends AnyFunSuite {

  private def evalMemo(pred: Expression,
                       table: TypedColTable)(memo: mutable.Map[Expression, mutable.BitSet])
  = {
    pred match {
      case t: TypedTupleBin =>
        memo.getOrElseUpdate(pred, {
          t.eval(table)
        })
      case c: TypedConstantBin =>
        memo.getOrElseUpdate(pred, {
          c.eval(table)
        })
    }
  }

  test("test correlation analysis") {
    val relpath = "datasets/hospital.csv"
    val miner = initMiner(relpath)
    //    val (supp, conf) = (1e-6, 0.85)
    //        val (rb,_) = doMineBatch(miner, "hospital.csv", supp = supp, conf = conf, 114514)
    //        val mineResult = miner.recoverConstants(ConstRecovArg(miner.getDataset, rb.mineResult, supp, conf, miner.getP2I))

    println("build memo...")
    val table = miner.getDataset
    val memo = TrieMap.empty[Expression, mutable.BitSet]
    val preds = miner.getP2I.getObjects
    val pb = new ProgressBar(preds.size)
    for (pred <- preds) {
      pred match {
        case t: TCalc =>
          t.getOp match {
            case Eq => evalMemo(pred, table)(memo)
            case _ => ()
          }
          pb += 1
        case _ => ???
      }
    }

    println("build memo done...")


    val r = for {
      (a, ia) <- memo
      (b, ib) <- memo
      if a != b

    } yield {
      (a, b, (ia & ib).size)
    }

    println(r.mkString("\n"))


  }

  test("Secure zone") {
    val relPath = "datasets/hospital.csv"
    val conf = 0.0d
    val supp = 0.0d
    val miner = initMiner(relPath)

    logger.info(miner.info())


    def secureZone(predecessor: PredicateSet, p: Expression) = {
      val p2i = predecessor.getP2I
      val i = p2i.getOrElse(p, ???)
      val rest = _allOnePredSet(p2i) ^ predecessor
      val secureZoneMask = PredicateSet.from(mutable.BitSet.fromSpecific(0 to i))(p2i)
      rest & (secureZoneMask)
    }

    miner.generateOneREE match {
      case ree@REE(x, rhs) =>
        logger.info(s"generated: ${ree}, selected p:${x.head}")
        logger.info(secureZone(x:-x.head, x.head))
    }


  }

}
