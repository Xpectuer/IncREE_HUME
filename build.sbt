ThisBuild / version := "0.2.0-CDF"
// ThisBuild / scalaVersion := "2.13.14"
ThisBuild / scalaVersion := "2.12.10"


import scala.sys.process.*

Test / logBuffered := false
fork / run := false
javaOptions / run := "-agentlib:hprof=cpu=samples,depth=8"

lazy val scalatest = "org.scalatest" %% "scalatest" % "3.2.18" % Test

def cleanOut = Command.command("cleanOut") {
  state =>
    ("rm -rf out/" !)
    state
}

commands += cleanOut

scalacOptions ++= Seq(
  //  "-Ytasty-reader",
)

lazy val root = (project in file("."))
  .settings(
    name := "HUME",
    resolvers += "Artima Maven Repository" at "https://repo.artima.com/releases",
    assembly / assemblyJarName := "HUME-CDF.jar",
    assembly / mainClass := Some("org.dsl.SparkMain"),
    assembly / assemblyJarName := {
      s"${name.value}-assembly-${version.value}.jar"
    },
    assembly / test := {},
    assembly / assemblyExcludedJars := {
      val cp = (assembly / fullClasspath).value
      cp.filter(_.data.getName == "scala-library.jar")
      cp.filter(_.data.getName == "z3")
    },
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs_*) => MergeStrategy.discard
      case x => MergeStrategy.first
    },


    libraryDependencies += scalatest,
    libraryDependencies += "org.scala-lang.modules" %% "scala-collection-compat" % "2.12.0",

    libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.6",
    libraryDependencies += "com.lihaoyi" %% "upickle" % "3.3.1",
    libraryDependencies += "net.sf.trove4j" % "core" % "3.1.0",
    libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "2.32.0",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.18" % "test",
    libraryDependencies += "org.scalanlp" %% "breeze" % "2.1.0" % "test",
    libraryDependencies += "org.scalanlp" %% "breeze-viz" % "2.1.0" % "test",
    libraryDependencies += "com.github.scopt" %% "scopt" % "4.1.0",

    libraryDependencies += "jline" % "jline" % "2.14.6",
    libraryDependencies += "org.apache.spark" %% "spark-core" % "3.5.1",
    libraryDependencies += "org.apache.spark" %% "spark-sql" % "3.5.1" % "provided",
    libraryDependencies += "org.apache.spark" %% "spark-mllib" % "3.5.1" % "provided",
    libraryDependencies += "software.amazon.awssdk" % "s3" % "2.25.27",

    libraryDependencies += "com.amazonaws" % "aws-java-sdk-s3" % "1.12.744",
    libraryDependencies += "com.tdunning" % "t-digest" % "3.3",


  )
















