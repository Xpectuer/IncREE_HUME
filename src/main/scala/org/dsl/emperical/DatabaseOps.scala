//package org.dsl.emperical
//
//import org.dsl.dataStruct.IdxPair.IdxPair
//import org.dsl.dataStruct.support.{ISupportSet, PairSupportSet, Universe}
//import org.dsl.dataStruct.{IdxPair, RBTreeMap, Statistics}
//import org.dsl.emperical.Database.logger
//import org.dsl.emperical.pli.Bucket
//import org.dsl.emperical.table.{ColTable, RowTable}
//import org.dsl.reasoning.predicate.{ColumnAtom, ConstantAtom, TableInstanceAtom}
//import org.dsl.utils.Sampling
//
//import scala.collection.mutable
//import scala.collection.mutable.ListBuffer
//
//object DatabaseOps {
//
//
//  private def doQueryMatchBucket(b1: Bucket,
//                                 b2: Bucket,
//                                 col1: ColumnAtom,
//                                 col2: ColumnAtom)
//                                (stat: Statistics): Statistics = {
//
//    if (b1.getName == b2.getName && col1 == col2) {
//
//      val seq1 = b1.getCol(col1).map(e => e._2)
//      val supp = seq1.map(e => {
//        val m = BigInt(e)
//        ((m - 1) << 1) + ((m - 1) * (m - 2) >> 1)
//      }).sum
//
//
//      val dag = b1.getDag
//
//      updateStat(stat, Universe(), supp)
//    } else {
//      // todo: dag
//      val seq1 = b1.getCol(col1).map(e => e._2)
//      val seq2 = b2.getCol(col2).map(e => e._2)
//      val pairs = seq1.zip(seq2)
//
//      val supp = pairs.map {
//        case (l, r) => l * r
//      }.sum
//
//      updateStat(stat, Universe(), supp)
//    }
//  }
//
//  // todo: DAG
//  def QueryMatchPartial1(spset: ISupportSet)
//                        (table1: Table,
//                         table2: Table,
//                         col1: ColumnAtom,
//                         col2: ColumnAtom)
//                        (stat: Statistics): Statistics = {
//
//    logger.debug(s"Query Match Partial: ${col1.getValue} and ${col2.getValue}")
//    table1 match {
//      case r1: RowTable =>
//        table2 match {
//          case r2: RowTable => ???
//          case _ => throw DatabaseException("Table Type Not Matched!")
//        }
//      case c1: ColTable =>
//        table2 match {
//          case c2: ColTable => doQueryMatchColPartial(spset)(c1, c2, col1, col2)(stat)
//          case _ => throw DatabaseException("Table Type Not Matched!")
//
//        }
//      case b1: Bucket =>
//        table2 match {
//          case b2: Bucket => doQueryMatchBucket(b1, b2, col1, col2)(stat)
//        }
//    }
//
//  }
//
//
//  private def doQueryMatchColPartial(spset: ISupportSet)
//                                    (t1: ColTable,
//                                     t2: ColTable,
//                                     col1: ColumnAtom,
//                                     col2: ColumnAtom)
//                                    (stat: Statistics): Statistics = {
//
//
//    val col1S = col1.value
//    val col2S = col2.value
//    val c1 = t1.getCol(col1S)
//    val c2 = t2.getCol(col2S)
//
//    logger.debug(s"Size of Column1 ${c1.size}, " +
//      s"Size of Column2 ${c2.size} ")
//
//    val newSpset = PairSupportSet.empty
//    val constBuffer = ListBuffer.empty[IdxPair]
//
//    spset.foreach {
//      case pair@(fst, snd) =>
//        val (idx1, idx2) = IdxPair.pairIdx(pair)
//
//        if (idx1 != idx2) {
//          if (c1(idx1) == c2(idx2)) {
//            newSpset.addOne(fst, snd)
//          }
//
//        } else { // idx1 == idx2
//          constBuffer.addOne(pair)
//        }
//
//      case _ => ()
//    }
//
//
//    for (e1 <- constBuffer; e2 <- constBuffer) {
//      val idx1 = e1._1._2
//      val idx2 = e2._1._2
//      val ee1 = c1(idx1)
//      val ee2 = c2(idx2)
//      if (ee1 == ee2) {
//        newSpset.addOne((e1._1, e2._1))
//      }
//    }
//
//
//    val support = newSpset.size
//
//    updateStat(stat, newSpset, support)
//  }
//
//
//  def QueryMatchFull1(schm1: TableInstanceAtom,
//                      table1: Table,
//                      schm2: TableInstanceAtom,
//                      table2: Table,
//                      col1: ColumnAtom,
//                      col2: ColumnAtom)
//                     (stat: Statistics): Statistics = {
//    logger.debug(s"Query Match Full: ${col1.getValue} and ${col2.getValue}")
//
//    table1 match {
//      case _: RowTable =>
//        table2 match {
//          case _: RowTable => ???
//          case _ => throw DatabaseException("Table Type Not Matched!")
//        }
//      case c1: ColTable =>
//        table2 match {
//          case c2: ColTable => doQueryMatchColFull(schm1, c1, schm2, c2, col1, col2)(stat)
//          case _ => throw DatabaseException("Table Type Not Matched!")
//
//        }
//      case b1: Bucket =>
//        table2 match {
//          case b2: Bucket => doQueryMatchBucket(b1, b2, col1, col2)(stat)
//          case _ => throw DatabaseException("Table Type Not Matched!")
//        }
//    }
//  }
//
//
//  private def matchSameTable(schm: TableInstanceAtom, c: RBTreeMap[String]): PairSupportSet = {
//    val spset = PairSupportSet.empty
//
//    for (e1 <- c.getTreeMap) {
//      for (e2 <- c.getTreeMap) {
//        val idx1 = e1._1
//        val idx2 = e2._1
//        if (idx1 != idx2 && c(idx1) == c(idx2)) {
//          val p = ((schm, idx1), (schm, idx2))
//          spset.addOne(p)
//        }
//      }
//    }
//
//    spset
//  }
//
//  private def splitRowtable(rowTable: RowTable, rate: Double): (RowTable, RowTable) = {
//    val incrSize: Int = (rate * rowTable.rowNum).toInt
//    val incrName: String = rowTable.getName + "_incr"
//
//    val restSize = rowTable.rowNum - incrSize
//    val restName: String = rowTable.getName + "_rest"
//
//    val (incr, rest) = Sampling.sampleSplitVanilla[List[(String, String)]](rowTable.getData, incrSize)
//    // logger.debug(incr)
//
//    (RowTable(incrName, incr), RowTable(restName, rest))
//  }
//
//  private def splitColtable(colTable: ColTable, rate: Double): (ColTable, ColTable) = {
//    val incrSize: Int = (rate * colTable.rowNum).toInt
//    val headers = colTable.getHeader
//    val incrName: String = colTable.getName + "_incr"
//
//    val restSize = colTable.rowNum - incrSize
//    val restName: String = colTable.getName + "_rest"
//
//    val data = colTable.getData
//
//    val t = colTable.getData.map { case (col, data) => col -> (data.take(incrSize), data.drop(incrSize)) }
//    val t1 = t.map {
//      case (col, p) => col -> p._1
//    }
//    val t2 = t.map {
//      case (col, p) => col -> p._2
//    }
//
//    (ColTable(incrName, t1, headers.toList), ColTable(restName, t2,  headers.toList ))
//  }
//
//  def SplitTable(table: Table, rate: Double): (Table, Table) = {
//    table match {
//      case r: RowTable => splitRowtable(r, rate)
//      case c: ColTable => splitColtable(c, rate)
//    }
//  }
//
//
//  // (incr, rest)
//  def SplitIntoTwoDB(db: Database, incrRate: Double): (Database, Database) = {
//
//    val m = db.mapExtract {
//      case (schm, table) =>
//        schm -> SplitTable(table, incrRate)
//    }
//
//    val incr: Database = Database {
//      mutable.Map.from(
//      m.map {
//        case (schm, p) => schm -> p._1
//      })
//    }
//    val rest: Database = Database {
//      mutable.Map.from(
//      m.map {
//        case (schm, p) => schm -> p._2
//      })
//    }
//
//    (incr, rest)
//  }
//
//  def BatchTranspose(db: Database): Database =
//    Database(
//      mutable.Map.from(
//      db.mapExtract {
//      case (schm, table) =>
//        val rightCol = table match {
//          case r: RowTable => r.transpose
//          case c: ColTable => c
//        }
//        schm -> rightCol
//    }))
//
//  private def matchTwoTables(
//                              schm1: TableInstanceAtom, c1: RBTreeMap[String],
//                              schm2: TableInstanceAtom, c2: RBTreeMap[String]) = {
//    val spset = PairSupportSet.empty
//
//    for (e1 <- c1.getTreeMap) {
//      for (e2 <- c2.getTreeMap) {
//        val idx1 = e1._1
//        val idx2 = e2._1
//        if (c1(idx1) == c2(idx2)) {
//          val p = ((schm1, idx1), (schm2, idx2))
//          spset.addOne(p)
//        }
//      }
//    }
//    spset
//  }
//
//
//  // todo: DAG
//  private def doQueryMatchColFull(schm1: TableInstanceAtom,
//                                  t1: ColTable,
//                                  schm2: TableInstanceAtom,
//                                  t2: ColTable,
//                                  col1: ColumnAtom,
//                                  col2: ColumnAtom)(stat: Statistics): Statistics = {
//    // logger.debug(s"QueryMatchFull: $col1 and $col2")
//    val col1S = col1.value
//    val col2S = col2.value
//
//    val c1 = t1.getCol(col1S)
//    val c2 = t2.getCol(col2S)
//    logger.debug(s"Size of Column1 ${c1.size}, Size of Column2 ${c2.size} ")
//
//    // O(N^2) elements
//
//    val spset = matchTwoTables(schm1, c1, schm2, c2)
//    val supp = spset.size
//
//    // todo: val newStat = Statistics(support, spset)
//    updateStat(stat, spset, supp)
//  }
//
//  def updateStat(old: Statistics, spset: ISupportSet, supp: BigInt) = {
//    val newStat = Statistics(supp, spset)
//    old.merge(newStat)
//    old
//  }
//
//
//  def QueryMatchConstFull(schm: TableInstanceAtom, table: Table,
//                          col: ColumnAtom, const: ConstantAtom)(stat: Statistics): Statistics = {
//    logger.debug(s"Query Match Constant Full: ${col.getValue} and ${const.getValue}")
//
//    table match {
//      case _: RowTable => throw DatabaseException("Row Table Not Supported")
//
//      case c: ColTable => doQueryMatchColConstFull(schm, c, col.getValue, const.getValue)(stat)
//
//      case b: Bucket => doQueryMatchBucketConst(b, col, const)(stat)
//
//    }
//  }
//
//  // todo: DAG
//  private def doQueryMatchBucketConst(b: Bucket, col: ColumnAtom, const: ConstantAtom)(stat: Statistics) = {
//    val supp = b.getCount(col, const)
//
//    updateStat(stat, Universe(), supp)
//    //    val newStat = Statistics(supp, Universe())
//    //    Statistics.update(stat, newStat)
//    //    stat
//  }
//
//
//  // todo: DAG
//  private def doQueryMatchColConstFull(schm: TableInstanceAtom, t1: ColTable,
//                                       colS: String, const: String)(stat: Statistics): Statistics = {
//
//
//    val c = t1.getCol(colS)
//    logger.debug(s"Size of Column ${c.size}")
//
//    val spset = PairSupportSet.empty
//
//    for (e <- c.getTreeMap) {
//      val ee = e._2
//      val idx = e._1
//      if (ee == const) {
//        spset.addOne(((schm, idx), (schm, idx)))
//      }
//
//    }
//
//    // O(N) elements
//    val support = spset.size
//
//    updateStat(stat, spset, support)
//
//
//  }
//
//  def QueryMatchConstPartial(spset: PairSupportSet)
//                            (table: Table,
//                             col: ColumnAtom,
//                             const: ConstantAtom)
//                            (stat: Statistics): Statistics = {
//    logger.debug(s"Query Match Constant Full: ${col.getValue} and ${const.getValue}")
//    table match {
//      case _: RowTable => throw DatabaseException("Row Table Not Supported")
//
//      case c: ColTable => doQueryMatchConstPartial(spset)(c, col.getValue, const.getValue)(stat)
//
//      case b: Bucket => doQueryMatchBucketConst(b, col, const)(stat)
//
//    }
//
//
//  }
//
//  private def throwIdxException = throw DatabaseException("Index Not Exists")
//
//
//  private def doQueryMatchConstPartial(spset: PairSupportSet)
//                                      (t: ColTable,
//                                       colS: String,
//                                       const: String)
//                                      (stat: Statistics): Statistics = {
//
//    val c = t.getCol(colS)
//    // logger.debug(c)
//
//    val newSpSet = PairSupportSet.empty
//    // todo: extends for multi-schema.
//    spset.foreach {
//      case pair@(fst, snd) =>
//        val (idx1, idx2) = IdxPair.pairIdx(pair)
//        val e1: String = c.getOrElse(idx1, throwIdxException)
//        val e2: String = c.getOrElse(idx2, throwIdxException)
//
//        if (e1 == e2 &&
//          e1 == const &&
//          e2 == const) {
//
//          newSpSet.addOne((fst, snd))
//        }
//      case _ => ()
//    }
//
//    val support = newSpSet.size
//    updateStat(stat, spset, support)
//  }
//}
//
