organization := "com.github.biopet"
organizationName := "Sequencing Analysis Support Core - Leiden University Medical Center"

startYear := Some(2017)

name := "PipelineStatus"
biopetUrlName := "pipelinestatus"

biopetIsTool := true

mainClass in assembly := Some("nl.biopet.tools.pipelinestatus.PipelineStatus")

developers := List(
  Developer(id="ffinfo", name="Peter van 't Hof", email="pjrvanthof@gmail.com", url=url("https://github.com/ffinfo"))
)

scalaVersion := "2.11.11"

libraryDependencies += "com.github.biopet" %% "tool-utils" % "0.2"
libraryDependencies += "com.typesafe.play" %% "play-ws" % "2.5.15"

libraryDependencies += "com.github.biopet" %% "tool-test-utils" % "0.1" % Test

//assemblyMergeStrategy in assembly := {
//  case PathList(ps @ _*) if ps.last endsWith "pom.properties" =>
//    MergeStrategy.first
//  case PathList(ps @ _*) if ps.last endsWith "pom.xml" =>
//    MergeStrategy.first
//  case x if Assembly.isConfigFile(x) =>
//    MergeStrategy.concat
//  case PathList(ps @ _*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) =>
//    MergeStrategy.rename
//  case PathList("META-INF", xs @ _*) =>
//    (xs map {_.toLowerCase}) match {
//      case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) =>
//        MergeStrategy.discard
//      case ps @ (x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
//        MergeStrategy.discard
//      case "plexus" :: xs =>
//        MergeStrategy.discard
//      case "services" :: xs =>
//        MergeStrategy.filterDistinctLines
//      case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) =>
//        MergeStrategy.filterDistinctLines
//      case _ => MergeStrategy.first
//    }
//  case _ => MergeStrategy.first
//}
