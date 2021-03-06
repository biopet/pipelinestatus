/*
 * Copyright (c) 2017 Biopet
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package nl.biopet.tools.pipelinestatus

import java.io.{File, PrintWriter}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import nl.biopet.utils.tool.ToolCommand
import nl.biopet.tools.pipelinestatus.pim._
import nl.biopet.tools.pipelinestatus.pim.{Job => PimJob}
import nl.biopet.utils.conversions
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process.Process

object PipelineStatus extends ToolCommand[Args] {

  def argsParser = new ArgsParser(this)

  def emptyArgs = Args()

  def main(args: Array[String]): Unit = {
    val cmdArgs = this.cmdArrayToArgs(args)

    logger.info("Start")

    implicit lazy val system: ActorSystem = ActorSystem()
    implicit lazy val materializer: ActorMaterializer = ActorMaterializer()
    implicit lazy val ws: AhcWSClient = AhcWSClient()

    val depsFile =
      cmdArgs.depsFile.getOrElse(getDepsFileFromDir(cmdArgs.pipelineDir))
    val deps = Deps.readDepsFile(depsFile)

    val pimRunId =
      if (cmdArgs.pimHost.isDefined) Some(cmdArgs.pimRunId.getOrElse {
        val graphDir = depsFile.getAbsoluteFile.getParentFile
        if (graphDir.getName == "graph")
          "biopet_" + graphDir.getParentFile.getName
        else "biopet_" + depsFile.getAbsolutePath.replaceAll("/", "_")
      })
      else None

    if (cmdArgs.pimHost.isDefined) {
      require(pimRunId.isDefined,
              "Could not auto-generate Pim run ID, please supply --pimRunId")
      logger.info(
        s"Status will be pushed to ${cmdArgs.pimHost.get}/run/${pimRunId.get}")
      if (cmdArgs.pimCompress)
        Await.result(deps.publishCompressedGraphToPim(cmdArgs.pimHost.get,
                                                      pimRunId.get,
                                                      cmdArgs.pimDeleteIfExist),
                     Duration.Inf)
      else
        Await.result(deps.publishGraphToPim(cmdArgs.pimHost.get,
                                            pimRunId.get,
                                            cmdArgs.pimDeleteIfExist),
                     Duration.Inf)
    }

    writePipelineStatus(
      deps,
      cmdArgs.outputDir,
      follow = cmdArgs.follow,
      refreshTime = cmdArgs.refreshTime,
      plots = cmdArgs.complatePlots,
      compressPlots = cmdArgs.compressPlots,
      pimHost = cmdArgs.pimHost,
      pimRunId = pimRunId,
      pimCompress = cmdArgs.pimCompress
    )

    ws.close()
    system.terminate()

    logger.info("Done")
  }

  def getDepsFileFromDir(pipelineDir: File): File = {
    require(pipelineDir.exists(), s"pipelineDir does not exist: $pipelineDir")
    val logDir = new File(pipelineDir, ".log")
    require(logDir.exists(), s"No .log dir found in pipelineDir")
    val runLogDir =
      logDir.list().sorted.map(new File(logDir, _)).filter(_.isDirectory).last
    val graphDir = new File(runLogDir, "graph")
    require(graphDir.exists(), s"Graph dir is not found: $graphDir")
    new File(graphDir, "deps.json")
  }

  def writePipelineStatus(deps: Deps,
                          outputDir: File,
                          alreadyDone: Set[String] = Set(),
                          alreadyFailed: Set[String] = Set(),
                          follow: Boolean = false,
                          refreshTime: Int = 30,
                          plots: Boolean = false,
                          compressPlots: Boolean = true,
                          pimHost: Option[String] = None,
                          pimRunId: Option[String] = None,
                          pimCompress: Boolean = false,
                          pimStatus: Map[String, JobStatus.Value] = Map())(
      implicit ws: AhcWSClient): Unit = {

    val jobDone = jobsDone(deps)
    val jobFailed = jobsFailed(deps, jobDone)
    val jobsStart = jobsReadyStart(deps, jobDone)

    var futures: List[Future[Any]] = Nil

    val jobsDeps = deps.jobs.map(x =>
      x._1 -> (x._2.dependsOnJobs match {
        case l: List[_] => l.map(_.toString)
      }))
    val jobsWriter = new PrintWriter(new File(outputDir, s"jobs.json"))
    jobsWriter.println(conversions.mapToJson(jobsDeps))
    jobsWriter.close()
    futures :+= writeGraphvizFile(new File(outputDir, s"jobs.gv"),
                                  jobDone,
                                  jobFailed,
                                  jobsStart,
                                  deps,
                                  plots,
                                  plots)
    futures :+= writeGraphvizFile(new File(outputDir, s"compress.jobs.gv"),
                                  jobDone,
                                  jobFailed,
                                  jobsStart,
                                  deps,
                                  compressPlots,
                                  compressPlots,
                                  compress = true)

    val mainJobs = deps.jobs.filter(_._2.mainJob == true).map {
      case (name, _) => name -> deps.getMainDependencies(name)
    }

    val mainJobsWriter = new PrintWriter(new File(outputDir, s"main_jobs.json"))
    mainJobsWriter.println(conversions.mapToJson(mainJobs))
    mainJobsWriter.close()
    futures :+= writeGraphvizFile(new File(outputDir, s"main_jobs.gv"),
                                  jobDone,
                                  jobFailed,
                                  jobsStart,
                                  deps,
                                  plots,
                                  plots,
                                  main = true)
    futures :+= writeGraphvizFile(new File(outputDir, s"compress.main_jobs.gv"),
                                  jobDone,
                                  jobFailed,
                                  jobsStart,
                                  deps,
                                  compressPlots,
                                  compressPlots,
                                  compress = true,
                                  main = true)

    val totalJobs = deps.jobs.size
    val totalStart = jobsStart.size
    val totalDone = jobDone.size
    val totalFailed = jobFailed.size
    val totalPending = totalJobs - jobsStart.size - jobDone.size - jobFailed.size

    futures.foreach(x => Await.result(x, Duration.Inf))

    pimHost.foreach { host =>
      val runId = pimRunId.getOrElse(
        throw new IllegalStateException(
          "Pim requires a run id, please supply this with --pimRunId"))

      val changes = (for (job <- deps.jobs) yield {
        val status = job._1 match {
          case n if jobsStart.contains(n) => JobStatus.running
          case n if jobFailed.contains(n) => JobStatus.failed
          case n if jobDone.contains(n)   => JobStatus.success
          case _                          => JobStatus.idle
        }

        if (!pimStatus.get(job._1).contains(status)) Some((job, status))
        else None
      }).flatten

      def statusToId(jobStatus: JobStatus.Value): Int = {
        jobStatus match {
          case JobStatus.idle    => 0
          case JobStatus.running => 1
          case JobStatus.success => 2
          case JobStatus.failed  => 3
        }
      }

      val future = if (changes.nonEmpty) {
        val payload = if (pimCompress) {
          changes
            .map(
              job =>
                PimJob(
                  name = job._1._1,
                  node =
                    if (job._1._2.configPath.nonEmpty)
                      "root" + job._1._2.configPath
                        .mkString("/", "/", "/") + job._1._2.compressedName._1
                    else "root/" + job._1._2.compressedName._1,
                  status = statusToId(job._2)
                ).toString)
            .mkString("[", ",", "]")
        } else {
          changes
            .map(
              job =>
                PimJob(
                  name = job._1._1,
                  node =
                    if (job._1._2.configPath.nonEmpty)
                      "root" + job._1._2.configPath
                        .mkString("/", "/", "/") + job._1._1
                    else "root/" + job._1._1,
                  status = statusToId(job._2)
                ).toString)
            .mkString("[", ",", "]")
        }
        val request = ws
          .url(s"$host/api/runs/$runId/jobs")
          .withHeaders("Accept" -> "application/json",
                       "Content-Type" -> "application/json")
          .put(payload)
          .map { r =>
            if (r.status == 200) logger.debug(r)
            else
              logger.warn(
                s"Put jobs did fail. Request: $r  Body: ${r.body}  payload: $payload")
            r
          }
        Some(request)
      } else None

      if (logger.isDebugEnabled) futures.foreach(_.onComplete(logger.debug(_)))
      future.foreach(Await.result(_, Duration.Inf))
    }
    logger.info(
      s"Total job: $totalJobs, Pending: $totalPending, Ready to run / running: $totalStart, Done: $totalDone, Failed $totalFailed")

    if (follow) {
      Thread.sleep(refreshTime * 1000)
      writePipelineStatus(deps,
                          outputDir,
                          jobDone,
                          jobFailed,
                          follow,
                          refreshTime,
                          plots,
                          compressPlots,
                          pimHost,
                          pimRunId,
                          pimCompress,
                          pimStatus)
    }
  }

  def writeGraphvizFile(outputFile: File,
                        jobDone: Set[String],
                        jobFailed: Set[String],
                        jobsStart: Set[String],
                        deps: Deps,
                        png: Boolean = true,
                        svg: Boolean = true,
                        compress: Boolean = false,
                        main: Boolean = false): Future[Unit] = Future {
    val graph =
      if (compress && main) deps.compressOnType(main = true)
      else if (compress) deps.compressOnType()
      else if (main) deps.getMainDeps
      else deps.jobs.map(x => x._1 -> x._2.dependsOnJobs)

    val writer = new PrintWriter(outputFile)
    writer.println("digraph graphname {")

    graph.foreach {
      case (job, jobDeps) =>
        // Writing color of node
        val compressTotal =
          if (compress)
            Some(deps.jobs.keys.filter(Job.compressedName(_)._1 == job))
          else None
        val compressDone =
          if (compress) Some(jobDone.filter(Job.compressedName(_)._1 == job))
          else None
        val compressFailed =
          if (compress) Some(jobFailed.filter(Job.compressedName(_)._1 == job))
          else None
        val compressStart =
          if (compress) Some(jobsStart.filter(Job.compressedName(_)._1 == job))
          else None
        val compressIntermediate =
          if (compress)
            Some(
              deps.jobs
                .filter(x => Job.compressedName(x._1)._1 == job)
                .forall(_._2.intermediate))
          else None

        if (compress) {
          val pend = compressTotal.get.size - compressFailed.get
            .diff(compressStart.get)
            .size - compressStart.get.size - compressDone.get.size
          writer.println(s"""  $job [label = "$job
                            |Total: ${compressTotal.get.size}
                            |Fail: ${compressFailed.get.size}
                            |Pend:$pend
                            |Run: ${compressStart.get
                              .diff(compressFailed.get)
                              .size}
                            |Done: ${compressDone.get.size}"]""".stripMargin)
        }

        if (jobDone.contains(job) || compress && compressTotal == compressDone)
          writer.println(s"  $job [color = green]")
        else if (jobFailed
                   .contains(job) || compress && compressFailed.get.nonEmpty)
          writer.println(s"  $job [color = red]")
        else if (jobsStart
                   .contains(job) || compress && compressTotal == compressStart)
          writer.println(s"  $job [color = orange]")

        // Dashed lined for intermediate jobs
        if ((deps.jobs.contains(job) && deps
              .jobs(job)
              .intermediate) || compressIntermediate.contains(true))
          writer.println(s"  $job [style = dashed]")

        // Writing Node deps
        jobDeps.foreach { dep =>
          if (compress) {
            val depsNames = deps.jobs
              .filter(x => Job.compressedName(x._1)._1 == dep)
              .filter(_._2.outputUsedByJobs.exists(x =>
                Job.compressedName(x)._1 == job))
              .map(x =>
                x._1 -> x._2.outputUsedByJobs.filter(x =>
                  Job.compressedName(x)._1 == job))
            val total = depsNames.size
            val done = depsNames
              .map(x => x._2.exists(y => jobDone.contains(x._1)))
              .count(_ == true)
              .toFloat / total
            val fail = depsNames
              .map(x => x._2.exists(y => jobFailed.contains(x._1)))
              .count(_ == true)
              .toFloat / total
            val start = (depsNames
              .map(x => x._2.exists(y => jobsStart.contains(x._1)))
              .count(_ == true)
              .toFloat / total) - fail
            if (total > 0)
              writer.println(
                s"""  $dep -> $job [color="red;%f:orange;%f:green;%f:black;%f"];"""
                  .format(fail, start, done, 1.0f - done - fail - start))
            else writer.println(s"  $dep -> $job;")
          } else writer.println(s"  $dep -> $job;")
        }
    }
    writer.println("}")
    writer.close()

    writeGvToPlot(outputFile, png, svg)
  }

  def writeGvToPlot(input: File,
                    png: Boolean = true,
                    svg: Boolean = true): Unit = {
    if (png)
      Process(Seq("dot", "-Tpng", "-O", input.getAbsolutePath))
        .run()
        .exitValue()
    if (svg)
      Process(Seq("dot", "-Tsvg", "-O", input.getAbsolutePath))
        .run()
        .exitValue()
  }

  def jobsReadyStart(deps: Deps, jobsDone: Set[String]): Set[String] = {
    deps.jobs
      .filterNot(x => jobsDone.contains(x._1))
      .filter(_._2.dependsOnJobs.forall(jobsDone))
      .keySet
  }

  def jobsDone(deps: Deps, alreadyDone: Set[String] = Set()): Set[String] = {
    val f = deps.jobs
      .filterNot(x => alreadyDone.contains(x._1))
      .map(x => x._2 -> x._2.isDone)
    val dones = f
      .map(x => x._1 -> Await.result(x._2, Duration.Inf))
      .filter(_._2)
      .map(_._1.name)
      .toSet ++ alreadyDone
    val f2 = f.map(x =>
      x._1 -> x._2.map { d =>
        if (d || !x._1.intermediate) d
        else upstreamJobDone(x._1, dones, deps)
    })
    val d = f2.map(x => x._1 -> Await.result(x._2, Duration.Inf))
    d.filter(_._2).map(_._1.name).toSet
  }

  private def upstreamJobDone(job: Job,
                              dones: Set[String],
                              deps: Deps): Boolean = {
    job.outputUsedByJobs
      .map(deps.jobs)
      .exists(
        x =>
          dones.contains(x.name) || (x.intermediate && upstreamJobDone(x,
                                                                       dones,
                                                                       deps)))
  }

  def jobsFailed(deps: Deps,
                 dones: Set[String],
                 alreadyFailed: Set[String] = Set()): Set[String] = {
    val f = deps.jobs
      .filterNot(x => dones.contains(x._1))
      .filterNot(x => alreadyFailed.contains(x._1))
      .map(x => x._1 -> x._2.isFailed)
    f.map(x => x._1 -> Await.result(x._2, Duration.Inf))
      .filter(_._2)
      .keySet ++ alreadyFailed
  }

  def descriptionText: String =
    """
                          | This tool keeps track of the status of a biopet pipeline.
                          |
                          | It also possible to push the status. For this a PIM host should be provided.
                        """.stripMargin

  def manualText: String =
    """
      | Default this tool will create the output just once. To do this continuously the -f argument should be given.
      |
      | This tool has also support for [PIM](https://git.lumc.nl/tkroes/pim), see examples for this.
    """.stripMargin

  def exampleText: String =
    s"""
                      | This will generate a default status check and trying to auto detect the graph:
                      | ${example("-d", "<pipeline_dir>", "-o", "<output_dir>")}
                      |
                      | This will follow the pipeline and update the results each interval:
                      | ${example("-d",
                                  "<pipeline_dir>",
                                  "-o",
                                  "<output_dir>",
                                  "-f")}
                      |
                      | To push to PIM this should be added:
                      | ${example("-d",
                                  "<pipeline_dir>",
                                  "-o",
                                  "<output_dir>",
                                  "-f",
                                  "--pimHost",
                                  "<pim_url>")}
                    """.stripMargin
}
