package org.dsl.utils

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.{AmazonS3Exception, PutObjectResult}
import com.github.tototoshi.csv.CSVReader
import org.dsl.dataStruct.evidenceSet.builders.{EvidenceSetBuilder, SplitReconsEviBuilder}
import org.dsl.dataStruct.evidenceSet.{HPEvidenceSet, IEvidenceSet}
import org.dsl.emperical.Database
import org.dsl.emperical.table.{ColTable, RowTable, TypedColTable}
import org.dsl.mining.PredSpace.{PredSpace, logger}
import org.dsl.mining.{CSVRowDebug, MineResultWithTime, ParameterLambda}
import org.dsl.reasoning.predicate.PredicateIndexProviderBuilder.PredicateIndexProvider
import org.dsl.reasoning.predicate.TableInstanceAtom

import java.io._
import java.nio.charset.CodingErrorAction
import java.nio.file.{Files, Path, Paths}
import scala.collection.compat._
import scala.collection.immutable.Iterable
import scala.collection.mutable
import scala.io.{Codec, Source}
import scala.util.{Failure, Success, Try}

object $ {

  def getTempDir(name: String): String = {
    val tempDirName = name
    val tempPath = Paths.get(System.getProperty("java.io.tmpdir"), tempDirName)

    if (!Files.exists(tempPath)) {
      Files.createDirectory(tempPath)
    }

    tempPath.toString
  }

  @deprecated
  def GetIndexOrElseBuild[R](filename: String, build: => R): R = {

    val dirpath = Paths.get(Config.CACHE_PATH)
    if (!Files.exists(dirpath)) {
      Files.createDirectory(dirpath)
    }

    val abspath = Paths.get(Config.CACHE_PATH, filename)

    if (Files.exists(abspath)) {
      println(s"cache hit: ${filename}")
      val ois = new ObjectInputStream(new FileInputStream(abspath.toString))
      val objRead = ois.readObject().asInstanceOf[R]
      ois.close()
      objRead
    } else {
      val obj = build
      Try {
        val oos = new ObjectOutputStream(new FileOutputStream(abspath.toString))
        oos.writeObject(obj)
        oos
      } match {
        case Success(oos) =>
          oos.close()
          obj
        case Failure(exception) =>
          Files.delete(abspath)
          throw exception
      }


    }
  }

  def readFile(filename: String): String = {
    val decoder = Codec.UTF8.decoder.onMalformedInput(CodingErrorAction.IGNORE)
    val source = Source.fromFile(filename)(decoder)
    try source.mkString finally source.close()
  }


  def writeFile(filename: String, content: String): Unit = {
    val path = Paths.get(filename)
    val parent = path.getParent
    if (parent == null || Files.notExists(parent)) Files.createDirectories(parent)
    val writer = new PrintWriter(filename)
    try {
      writer.write(content)
    } finally {
      writer.close()
    }
  }

  def CleanDir(pathname: String): Unit = {
    import java.nio.file.{Files, Paths, Path}
    import java.nio.file.attribute.BasicFileAttributes
    import java.nio.file.FileVisitResult
    import java.nio.file.SimpleFileVisitor
    // Specify the directory you want to clean
    val directoryToClean = Paths.get(pathname)

    // Check if the directory exists
    if (Files.exists(directoryToClean) && Files.isDirectory(directoryToClean)) {
      // Use a SimpleFileVisitor to visit each file and delete it
      Files.walkFileTree(directoryToClean, new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

//        override def postVisitDirectory(dir: Path, exc: java.io.IOException): FileVisitResult = {
//          // After all files in the directory are deleted, delete the directory itself
//          Files.delete(dir)
//          FileVisitResult.CONTINUE
//        }
      })
      println(s"All files under '$directoryToClean' have been deleted.")
    } else {
      println(s"The directory '$directoryToClean' does not exist or is not a directory.")
    }
  }



  def CleanResultJson(filename: String) = {
    if (Config.ENABLE_DEBUG) {
      val dirpath = Paths.get(Config.RAW_RESULT_PATH)
      if (!Files.exists(dirpath)) {
        Files.createDirectory(dirpath)
      }

      val path = Paths.get(Config.RAW_RESULT_PATH, filename)

      if(Files.exists(path)) Files.delete(path)
      logger.debug(s"Cleaned ${path.toString}")
    }
  }

  @deprecated
  def WriteDebugDataRow(dataRowDebug: CSVRowDebug): Unit = {
    dataRowDebug match {
      case CSVRowDebug(dataName, oldSupp, newSupp, oldConf, newConf, dist, recall, _, _, _, _, _) =>
        val fileName = s"[debug]${dataName}_${oldSupp}_${oldConf}_${newSupp}_${newConf}_${dist}_${recall}.csv"
        val header = "Dataset,OldSupp,NewSupp,OldConf,NewConf,Batch1 Time,Pruned set,Sampled Set,Batch2,Inc Time"
        $.WriteResult(fileName, header + "\n" + dataRowDebug.toString)
    }
  }

  def GetResultJsonOrElseMine(filename: String, p2i: PredicateIndexProvider, mine: => MineResultWithTime): MineResultWithTime = {
    val dirpath = Paths.get(Config.RAW_RESULT_PATH)
    if (!Files.exists(dirpath)) {
      Files.createDirectory(dirpath)
    }

    val abspath = Paths.get(Config.RAW_RESULT_PATH, filename)

    logger.info(s"Finding ${abspath}...")

    if (Config.USE_CKPT && Files.exists(abspath)) {
      val j = readFile(abspath.toString)
      val mwt: MineResultWithTime = MineResultWithTime.fromJSON(j, p2i)
      logger.info(s"cache hit: ${filename}, size: ${mwt.mineResult.result.size}, time: ${mwt.totalTime}")
      mwt

    } else {
      logger.info(s"cache miss ${filename}, start mining...")
      val obj = mine
      val s = obj.toJSON

      //      Files.write(abspath, s.getBytes())
      writeFile(abspath.toString, s)
      obj
    }
  }


  def GetJsonIndexOrElseBuild(filename: String, p2i: PredicateIndexProvider, build: => IEvidenceSet): IEvidenceSet = {

    val dirpath = Paths.get(Config.CACHE_PATH)
    if (!Files.exists(dirpath)) {
      Files.createDirectory(dirpath)
    }

    val abspath = Paths.get(Config.CACHE_PATH, filename)

    if (Files.exists(abspath)) {

      val j = readFile(abspath.toString)
      val evi = HPEvidenceSet.fromJSON(j)(p2i)
      logger.info(s"cache hit: ${filename}, size: ${evi.size}")
      evi

    } else {

      logger.info(s"cache miss ${filename}, start evidenced build...")

      val obj = build
      val s = obj.jsonify
      //      Files.write(abspath, s.getBytes())
      writeFile(abspath.toString, s)
      obj
    }
  }

  def defaultEvidenceBuild(data:TypedColTable, predSpace: PredSpace, p2i:PredicateIndexProvider): IEvidenceSet = {

    val evb = EvidenceSetBuilder(data, predSpace, SplitReconsEviBuilder.FRAG_SIZE)(p2i)
    logger.info(s"Evidence Building With PredSpace ${predSpace.values.map(_.size).sum}...")
    val (ret, time) = Wrappers.timerWrapperRet(SplitReconsEviBuilder.buildFullEvi(evb, data.rowNum))
    logger.info(s"Evidence Building Time $time; With Evidence Size: ${ret.size}")
    $.WriteResult("evidence.txt", ret.mkString(",\n"))
    ret
  }

  private def initS3Client = {
    // Set up AWS credentials
    val accessKey = Config.AWSAccessKey
    val secretKey = Config.AWSAccessSecretKey
    val awsCredentials = new BasicAWSCredentials(accessKey, secretKey)

    // Build the S3 client
    val s3Client = AmazonS3ClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
      .withRegion("ap-southeast-2") // replace with your desired region
      .build()
    s3Client
  }

  def bannerApriori() = {
    println(
      """
        |dP  .d888888                    oo                   oo
        |88 d8'    88
        |88 88aaaaa88a 88d888b. 88d888b. dP .d8888b. 88d888b. dP
        |88 88     88  88'  `88 88'  `88 88 88'  `88 88'  `88 88
        |88 88     88  88.  .88 88       88 88.  .88 88       88
        |dP 88     88  88Y888P' dP       dP `88888P' dP       dP
        |              88
        |              dP
        |""".stripMargin
    )
  }
  def banner = {
    println(
      """
        |██╗  ██╗██╗   ██╗███╗   ███╗███████╗
        |██║  ██║██║   ██║████╗ ████║██╔════╝
        |███████║██║   ██║██╔████╔██║█████╗
        |██╔══██║██║   ██║██║╚██╔╝██║██╔══╝
        |██║  ██║╚██████╔╝██║ ╚═╝ ██║███████╗
        |╚═╝  ╚═╝ ╚═════╝ ╚═╝     ╚═╝╚══════╝
        |
        |""".stripMargin)
  }


  def readParam(fileRelPath: String): Iterable[ParameterLambda] = {

    val path = Paths.get(fileRelPath).toString
    val src = Source.fromFile(new File(path))
    val reader = CSVReader.open(src)
    val raw = reader.all()
    val filteredHead = raw.map {
      l: List[String] =>
        l.map(p => Try(p.toDouble)).filter(_.isSuccess).map(_.get)
    }

    println("filtered Head:", filteredHead)

    val r = filteredHead.map {
      case List(suppOld, suppNew, confOld, confNew, recall, dist) =>
        Some(ParameterLambda(suppOld, suppNew, confOld, confNew, recall, dist.toInt))
      case Nil =>
        None
    }.filter(_.isDefined).map(_.get)

    r

  }


  def GetJsonIndexFromS3OrElseBuild(filename: String, p2i: PredicateIndexProvider, build: => IEvidenceSet): IEvidenceSet = {

    val dirpath = Paths.get(Config.CACHE_PATH)

    // todo: cancel lines, create dir on s3  ?
    //    if (!Files.exists(dirpath)) {
    //      Files.createDirectory(dirpath)
    //    }


    //val abspath = Paths.get(Config.CACHE_PATH, filename)


    // Build the S3 client
    val s3Client = initS3Client

    //    paths.map(path => {
    //      // Get the file from S3
    //      val s3Object = s3Client.getObject(Config.AWSS3BucketName, path)
    //      val rowTable = RowTable(s3Object)
    //      TypedColTable.from(rowTable)
    //    })

    val key = "index/" + filename


    logger.info(s"Check And Get if evidence set of dataset $key Exists..")
    try {
      if (s3Client.doesObjectExist(Config.AWSS3BucketName, key)) {
        val s3Obj = s3Client.getObject(Config.AWSS3BucketName, key)

        val s3ObjContent = s3Obj.getObjectContent

        val bSource = Source.fromInputStream(s3ObjContent)
        val text = bSource.getLines().mkString("\n")

        bSource.close()
        val evi = HPEvidenceSet.fromJSON(text)(p2i)
        logger.info(s"cache hit: ${filename}, size: ${evi.size}")
        evi

      } else {

        val obj = build
        val s = obj.jsonify
        ???
        obj
      }
    } catch {
      case e: AmazonS3Exception =>
        logger.fatal(s"Amazon S3 error: ${e.getMessage}")
        HPEvidenceSet()
      case e: IOException =>
        logger.fatal(s"I/O error: ${e.getMessage}")
        HPEvidenceSet()
      case e: Exception =>
        throw e
    }


  }


  def ReadParamInput(filename: String) = {

    val bufferedSource = Source.fromFile(filename)

    val l =
      (for {
        line <- bufferedSource.getLines().drop(1) // Drop the header line
        cols = line.split(",").map(_.trim) // Split each line by comma and trim spaces
      } yield cols.map(_.toDouble)).toList // Convert each pair of columns to Double and yield a tuple

    val (supp, conf) = (l.map(p => (p(0), p(1))), l.map(p => (p(2), p(3))))

    bufferedSource.close
    (supp, conf)
  }


  def ReadParamInputYaml(yaml: String): List[(Double, Double)] = {

    val bufferedSource = Source.fromFile(yaml)

    val data: List[(Double, Double)] =
      (for {
        line <- bufferedSource.getLines().drop(1) // Drop the header line
        cols = line.split(",").map(_.trim) // Split each line by comma and trim spaces
      } yield (cols(0).toDouble, cols(1).toDouble)).toList // Convert each pair of columns to Double and yield a tuple

    bufferedSource.close
    data
  }

  def findFileOrElse(relpath: String,
                     actionNoExist: Path => Unit,
                     postHook: Path => Unit): Unit = {
    val path = Paths.get(addProjPath(relpath))
    if (!Files.exists(path)) {
      //      Files.createFile(path)
      actionNoExist(path)
    }
    postHook(path)

  }


  def WriteResult(filename: String, data: String) = {
    val relpath = Config.OUTDIR + s"/$filename"
    //findFileOrElse(relpath,
    //  path => Files.createFile(path),
    //  path => Files.write(path, data.getBytes(StandardCharsets.UTF_8)))

    writeFile(relpath, data)
  }

  def WriteResultToS3(key: String, data: String): Unit = {
    val s3Client = initS3Client

    // Get the file from S3
    logger.info(s"Writing To S3: $key...")
    val s3Object: PutObjectResult = s3Client.putObject(Config.AWSS3BucketName, key, data)
    logger.info(s"Put Object Result: \n${s3Object.getMetadata.getArchiveStatus}\n=======================")
  }

  lazy val PROJ_PATH: String = Paths.get(".").toAbsolutePath.toString

  def addProjPath(path: String): String =
    Paths.get(PROJ_PATH, path).toString

  def LoadDataSet(tableName: String, dbPath: String): Database = {

    val rowTable = RowTable(dbPath)

    val schmTableArrow = (TableInstanceAtom(tableName), rowTable)
    val map = Map(schmTableArrow)
    val db = Database(mutable.Map.from(map))
    db
  }

  def LoadDataSet(dbPaths: Iterable[String]): Iterable[TypedColTable] = {

    dbPaths.map {

      s =>
        val p = Paths.get(s)
        if (Files.exists(p)) {
          Some(p)
        } else {
          None
        }
    }
      .map {
        case Some(path) =>
          val colTab = ColTable(path.toString)
          //logger.info("DEBUG: " + colTab.rowNum)
          TypedColTable.from(colTab)

        case None => throw new FileNotFoundException()
      }

  }

  def LoadDataSet(path: String): Option[TypedColTable] = {
    LoadDataSet(Vector(path)) match {
      case i: Iterable[TypedColTable] if i.nonEmpty => Some(i.head)
      case _ => None
    }
  }

  def permutations(n: Int): BigInt =
    (1 to n).map(BigInt(_)).foldLeft(BigInt(1))(_ * _)

  def combinations(n: Int, k: Int): BigInt =
    permutations(n) / (permutations(k) * permutations(n - k))

  def binom(n: Int, k: Int): BigInt = {
    require(0 <= k && k <= n)

    @annotation.tailrec
    def binomtail(nIter: Int, kIter: Int, ac: BigInt): BigInt = {
      if (kIter > k) ac
      else binomtail(nIter + 1, kIter + 1, (nIter * ac) / kIter)
    }

    if (k == 0 || k == n) 1
    else binomtail(n - k + 1, 1, BigInt(1))
  }

  // todo: do not push to github repo
  def LoadDataSetFromS3(paths: Iterable[String]): Iterable[TypedColTable] = {
    // Set up AWS credentials
    val accessKey = Config.AWSAccessKey
    val secretKey = Config.AWSAccessSecretKey
    val awsCredentials = new BasicAWSCredentials(accessKey, secretKey)

    // Build the S3 client
    val s3Client = AmazonS3ClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
      .withRegion("ap-southeast-2") // replace with your desired region
      .build()


    paths.map(path => {
      // Get the file from S3
      logger.info(s"Getting From S3: $path...")
      val s3Object = s3Client.getObject(Config.AWSS3BucketName, path)
      logger.info(s"Get S3 Object Type: \n${s3Object.getObjectMetadata.getContentType}\n=======================")

      TypedColTable.from(ColTable(s3Object))
    })


  }


  // Assume all file is in csv
  def GetDSName(path: String): String = Paths.get(path).getFileName.toString.split('.').head


  def LoadTable(dbPath: String): RowTable = RowTable(dbPath)

}





