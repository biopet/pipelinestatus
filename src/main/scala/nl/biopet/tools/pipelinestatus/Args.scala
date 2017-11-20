package nl.biopet.tools.pipelinestatus

import java.io.File

case class Args(pipelineDir: File = null,
                depsFile: Option[File] = None,
                outputDir: File = null,
                follow: Boolean = false,
                refreshTime: Int = 30,
                complatePlots: Boolean = false,
                compressPlots: Boolean = true,
                pimHost: Option[String] = None,
                pimRunId: Option[String] = None,
                pimDeleteIfExist: Boolean = false,
                pimCompress: Boolean = false)
