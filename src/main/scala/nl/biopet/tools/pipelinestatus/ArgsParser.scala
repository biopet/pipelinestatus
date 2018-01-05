package nl.biopet.tools.pipelinestatus

import java.io.File

import nl.biopet.utils.tool.{AbstractOptParser, ToolCommand}

class ArgsParser(toolCommand: ToolCommand[Args]) extends AbstractOptParser[Args](toolCommand) {
  opt[File]('d', "pipelineDir") required () maxOccurs 1 valueName "<file>" action {
    (x, c) =>
      c.copy(pipelineDir = x)
  } text "Output directory of the pipeline"
  opt[File]('o', "outputDir") required () maxOccurs 1 valueName "<file>" action {
    (x, c) =>
      c.copy(outputDir = x)
  } text "Output directory of this tool"
  opt[File]("depsFile") maxOccurs 1 valueName "<file>" action { (x, c) =>
    c.copy(depsFile = Some(x))
  } text "Location of deps file, not required"
  opt[Unit]('f', "follow") maxOccurs 1 action { (_, c) =>
    c.copy(follow = true)
  } text "This will follow a run"
  opt[Int]("refresh") maxOccurs 1 action { (x, c) =>
    c.copy(refreshTime = x)
  } text "Time to check again, default set on 30 seconds"
  opt[Unit]("completePlots") maxOccurs 1 action { (_, c) =>
    c.copy(complatePlots = true)
  } text "Add complete plots, this is disabled because of performance. " +
    "Complete plots does show each job separated, while compressed plots collapse all jobs of the same type together."
  opt[Unit]("skipCompressPlots") maxOccurs 1 action { (_, c) =>
    c.copy(compressPlots = false)
  } text "Disable compressed plots. By default compressed plots are enabled."
  opt[String]("pimHost") maxOccurs 1 action { (x, c) =>
    c.copy(pimHost = Some(x.stripSuffix("/")))
  } text "Pim host to publish status to"
  opt[String]("pimRunId") maxOccurs 1 action { (x, c) =>
    c.copy(pimRunId = Some(x))
  } text "Pim run Id to publish status to"
  opt[Unit]("pimDeleteIfExist") maxOccurs 1 action { (x, c) =>
    c.copy(pimDeleteIfExist = true)
  } text "Delete run if it already exists"
  opt[Unit]("pimFullGraph") maxOccurs 1 action { (x, c) =>
    c.copy(pimCompress = false)
  } text "Compress nodes to publish to PIM"
}
