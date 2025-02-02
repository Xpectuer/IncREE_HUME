//import org.dsl.reasoning.{TestRuleInterpreter, TestTreeBuild}
//import org.dsl.utils.{Config, HUMELogger, TestReporter, Wrappers}
//import org.scalatest.Tag
//import org.scalatest.funsuite.AnyFunSuite

//class Test extends AnyFunSuite {
//  val logger: HUMELogger = HUMELogger(getClass.getName)
//
//  test("Test AIRPORT Incremental example") {
//    // test dataset with small number of rows
//    // size: 500
//    val duration = Wrappers.timerWrapper0(
//      () => {
//        TestRuleInterpreter.TestIncremental(Config.addProjPath("/datasets/airport_full.csv"),
//          Config.addProjPath("/rules/labeled_data_400/airports/train/rules.txt"),
//          0.01, 0.1)
//      })
//
//    TestReporter.reportTestTime(logger, "Test AIRPORT example", duration)
//  }
//
//  test("Test AIRPORT 10% sample 1% incremental") {
//    // test dataset with small number of rows
//    // size: 500
//    val duration = Wrappers.timerWrapper0(
//      () => {
//        TestRuleInterpreter.TestIncremental(Config.addProjPath("/datasets/airport_full.csv"),
//          Config.addProjPath("/rules/labeled_data_400/airports/train/rules.txt"),
//          0.1, 0.01)
//      })
//
//    TestReporter.reportTestTime(logger, "Test AIRPORT example", duration)
//  }
//
//  test("Test AIRPORT 10% sample 10% incremental") {
//    // test dataset with small number of rows
//    // size: 500
//    val duration = Wrappers.timerWrapper0(
//      () => {
//        TestRuleInterpreter.TestIncremental(Config.addProjPath("/datasets/airport_full.csv"),
//          Config.addProjPath("/rules/labeled_data_400/airports/train/rules.txt"),
//          0.1, 0.1)
//      })
//
//    TestReporter.reportTestTime(logger, "Test AIRPORT example", duration)
//  }
//
//  test("Test AIRPORT 10% sample 100% incremental") {
//    // test dataset with small number of rows
//    // size: 500
//    val duration = Wrappers.timerWrapper0(
//      () => {
//        TestRuleInterpreter.TestIncremental(Config.addProjPath("/datasets/airport_full.csv"),
//          Config.addProjPath("/rules/labeled_data_400/airports/train/rules.txt"),
//          0.1, 1.0)
//      })
//
//    TestReporter.reportTestTime(logger, "Test AIRPORT example", duration)
//  }
//
//  test("TEST Bucket example") {
//    val duration = Wrappers.timerWrapper0(
//      () => {
//        TestRuleInterpreter.TestBucket(Config.addProjPath("/datasets/airport_full.csv"),
//          Config.addProjPath("/rules/labeled_data_400/airports/train/rules.txt"))
//      })
//
//    TestReporter.reportTestTime(logger, "Test AIRPORT example", duration)
//  }
//
//  test("TEST Bucket TREE example") {
//    val duration = Wrappers.timerWrapper0(
//      () => {
//        TestTreeBuild.TestBucketTree(Config.addProjPath("/datasets/airport_full.csv"),
//          Config.addProjPath("/rules/labeled_data_400/airports/train/rules.txt"))
//      })
//
//    TestReporter.reportTestTime(logger, "Test AIRPORT TREE example", duration)
//  }
//
//  test("TEST Bucket TREE Entropy example") {
//    val duration = Wrappers.timerWrapper0(
//      () => {
//        TestTreeBuild.TestBucketEntropySort(Config.addProjPath("/datasets/airport_full.csv"),
//          Config.addProjPath("/rules/labeled_data_400/airports/train/rules.txt"))
//      })
//
//    TestReporter.reportTestTime(logger, "Test AIRPORT Entropy example", duration)
//  }
//
//  test("TEST TREE build Hospital") {
//    val duration = Wrappers.timerWrapper0(
//      () => {
//        TestTreeBuild.TestTreeBuild(Config.addProjPath("/datasets/hospital/hospital.csv"),
//          Config.addProjPath("/rules/labeled_data_400/hospital/train/rules.txt"))
//      })
//
//    TestReporter.reportTestTime(logger, "Test HOSPITAL", duration)
//  }
//
//  test("Test AIRPORT 0.1%") {
//    // test dataset with small number of rows
//    // size: 500
//    val duration = Wrappers.timerWrapper0(
//      () => {
//        TestRuleInterpreter.TestAirportRate(Config.addProjPath("/datasets/airport_full.csv"),
//          Config.addProjPath("/rules/labeled_data_400/airports/train/rules.txt"),
//          0.001)
//      })
//
//    TestReporter.reportTestTime(logger, "Test AIRPORT example", duration)
//  }
//
//  test("Test TREE BUILD 0.01%") {
//    // test dataset with small number of rows
//    // size: 500
//    val duration = Wrappers.timerWrapper0(
//      () => {
//        TestTreeBuild.TestTreeBuildEvalRate(Config.addProjPath("/datasets/airport_full.csv"),
//          Config.addProjPath("/rules/labeled_data_400/airports/train/rules.txt"),
//          0.001)
//      })
//
//    TestReporter.reportTestTime(logger, "Test AIRPORT example", duration)
//  }
//
//  test("Test AIRPORT 1%") {
//    // test dataset with small number of rows
//    // size: 500
//    val duration = Wrappers.timerWrapper0(
//      () => {
//        TestRuleInterpreter.TestAirportRate(Config.addProjPath("/datasets/airport_full.csv"),
//          Config.addProjPath("/rules/labeled_data_400/airports/train/rules.txt"),
//          0.01)
//      })
//
//    TestReporter.reportTestTime(logger, "Test AIRPORT example", duration)
//  }
//
//  test("Test TREE BUILD 1%") {
//    // test dataset with small number of rows
//    val duration = Wrappers.timerWrapper0(
//      () => {
//        TestRuleInterpreter.TestAirportRate(Config.addProjPath("/datasets/airport_full.csv"),
//          Config.addProjPath("/rules/labeled_data_400/airports/train/rules.txt"),
//          0.01)
//      })
//
//    TestReporter.reportTestTime(logger, "Test TREE BUILD example", duration)
//  }
//
//
//  test("Test TREE BUILD 10%", Tag("TREE10")) {
//
//    // test dataset with small number of rows
//    val duration = Wrappers.timerWrapper0(
//      () => {
//        TestTreeBuild.TestTreeBuildEvalRate(Config.addProjPath("/datasets/airport_full.csv"),
//          Config.addProjPath("/rules/labeled_data_400/airports/train/rules.txt"),
//          0.1)
//      })
//    TestReporter.reportTestTime(logger, "Test BUILD TREE 10%", duration)
//
//  }
//
//  test("Test AIRPORT 10%") {
//    val duration = Wrappers.timerWrapper0(
//      () => {
//        TestRuleInterpreter.TestAirportRate(Config.addProjPath("/datasets/airport_full.csv"),
//          Config.addProjPath("/rules/labeled_data_400/airports/train/rules.txt"),
//          0.1)
//      })
//
//    TestReporter.reportTestTime(logger, "Test AIRPORT %10", duration)
//  }
//
//  test("Test AIRPORT 100%") {
//    val duration = Wrappers.timerWrapper0(
//      () => {
//        TestRuleInterpreter.TestAirport(Config.addProjPath("/datasets/airport_full.csv"),
//          Config.addProjPath("/rules/labeled_data_400/airports/train/rules.txt"))
//      })
//
//    TestReporter.reportTestTime(logger, "Test AIRPORT FULL", duration)
//  }
//
//  //  // fixme: out of memory  100G
//  test("Test TREE BUILD 100%", Tag("TREE100")) {
//    // test dataset with small number of rows
//    val duration = Wrappers.timerWrapper0(
//      () => {
//        TestTreeBuild.TestTreeBuildEvalFull(Config.addProjPath("/datasets/airport_full.csv"),
//          Config.addProjPath("/rules/labeled_data_400/airports/train/rules.txt"))
//      })
//    TestReporter.reportTestTime(logger, "Test BUILD TREE", duration)
//  }
//
//
//}