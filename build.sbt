organization := "com.github.biopet"
organizationName := "Biopet"

startYear := Some(2017)

name := "PipelineStatus"
biopetUrlName := "pipelinestatus"

biopetIsTool := true

mainClass in assembly := Some("nl.biopet.tools.pipelinestatus.PipelineStatus")

developers := List(
  Developer(id="ffinfo", name="Peter van 't Hof", email="pjrvanthof@gmail.com", url=url("https://github.com/ffinfo"))
)

scalaVersion := "2.11.11"

libraryDependencies += "com.github.biopet" %% "tool-utils" % "0.3-SNAPSHOT" changing()
libraryDependencies += "com.typesafe.play" %% "play-ws" % "2.5.15"

libraryDependencies += "com.github.biopet" %% "tool-test-utils" % "0.2-SNAPSHOT" % Test changing()
