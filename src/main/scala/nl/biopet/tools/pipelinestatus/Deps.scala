package nl.biopet.tools.pipelinestatus

import java.io.File

import nl.biopet.tools.pipelinestatus.pim._
import nl.biopet.utils.Logging
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.libs.ws.WSResponse
import play.api.libs.ws.ahc.AhcWSClient
import nl.biopet.tools.pipelinestatus.pim.{Job => PimJob}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source

/**
  * This class can store the deps.json from a pipeline that stores all jobs and files and the connections
  *
  * Created by pjvanthof on 24/06/2017.
  */
case class Deps(jobs: Map[String, Job], files: Array[JsObject])
    extends Logging {

  /**
    * This method will compress the graph by combining all common job names
    * @param main When set true the non main jobs will be skipped in the graph
    * @return List of dependencies
    */
  def compressOnType(main: Boolean = false): Map[String, List[String]] = {
    (for ((_, job) <- jobs.toSet if !main || job.mainJob) yield {
      job.name -> (if (main)
                     getMainDependencies(job.name).map(
                       Job.compressedName(_)._1)
                   else job.dependsOnJobs.map(Job.compressedName(_)._1))
    }).groupBy(x => Job.compressedName(x._1)._1)
      .map(x => x._1 -> x._2.flatMap(_._2).toList.distinct)
  }

  /** this will return all main dependencies */
  def getMainDeps: Map[String, List[String]] = {
    jobs.filter(_._2.mainJob).map(x => x._1 -> getMainDependencies(x._1))
  }

  /**
    * This will return for a single job the main dependencies.
    * When a job depend on a non main job it will take the dependencies from that job till it finds a main dependency
    */
  def getMainDependencies(jobName: String): List[String] = {
    val job = this.jobs(jobName)
    val dependencies = job.dependsOnJobs match {
      case l: List[_] => l.map(_.toString)
    }
    dependencies.flatMap { dep =>
      if (this.jobs(dep).mainJob) List(dep)
      else getMainDependencies(dep)

    }.distinct
  }

  def makePimRun(runId: String): Run = {
    val links: Array[Link] =
      this.jobs.values
        .flatMap(x => x.dependsOnJobs.map(y => (x.name, y)))
        .map {
          case (toNode, fromNode) =>
            Link(
              fromPort =
                if (jobs(fromNode).configPath.nonEmpty)
                  "root" + jobs(fromNode).configPath
                    .mkString("/", "/", "/") + fromNode + "/output"
                else "root/" + fromNode + "/output",
              toPort =
                if (jobs(toNode).configPath.nonEmpty)
                  "root" + jobs(toNode).configPath
                    .mkString("/", "/", "/") + toNode + "/input"
                else "root/" + toNode + "/input"
            )
        }
        .toArray

    def jobsToNode(jobs: List[Job], depth: Int = 0): Array[Node] = {
      val groups = jobs.groupBy(_.configPath.lift(depth))

      // Getting jobs from this node
      val jobsNodes = groups
        .filter(_._1.isEmpty)
        .flatMap(_._2)
        .map(
          job =>
            Node(name = job.name,
                 inPorts = Array(Port(name = "input", title = Some("input"))),
                 outPorts = Array(Port(name = "output", title = Some("output"))),
                title = Some(job.name)
            ))

      // Getting all sub nodes
      val subNodes = groups
        .filter(_._1.isDefined)
        .map(g =>
          Node(title = Some(g._1.get), name = g._1.get, children = jobsToNode(g._2, depth + 1)))

      (jobsNodes ++ subNodes).toArray
    }

    Run(
      name = runId,
      user = "biopet",
      root = Node(
        name = "root",
        title = Some("root"),
        children = jobsToNode(jobs.values.toList)
      ),
      links = links
    )
  }

  def publishGraphToPim(host: String,
                        runId: String,
                        deleteIfExist: Boolean = false)(
      implicit ws: AhcWSClient): Future[WSResponse] = {

    publishRunToPim(makePimRun(runId), host, deleteIfExist).flatMap { r =>
      if (r.status != 200)
        throw new IllegalStateException(
          s"Post workflow did fail. Request: $r  Body: ${r.body}")
      val payload = jobs
        .map(
          job =>
            PimJob(
              name = job._1,
              title = Some(job._1),
              description = Some(job._1),
              node =
                if (job._2.configPath.nonEmpty)
                  "root" + job._2.configPath.mkString("/", "/", "/") + job._1
                else "root/" + job._1,
              status = 0
            ).toString)
        .mkString("[", ",", "]")
      ws.url(s"$host/api/runs/$runId/jobs")
        .withHeaders("Accept" -> "application/json",
                     "Content-Type" -> "application/json")
        .post(payload)
        .map { r =>
          if (r.status == 200) logger.debug(r)
          else
            logger.warn(
              s"Post jobs did fail. Request: $r  Body: ${r.body}  payload: $payload")
          r
        }
    }
  }

  def makeCompressedPimRun(runId: String): Run = {
    val links: Array[Link] =
      this.jobs.values
        .flatMap(x => x.dependsOnJobs.map(y => (x.name, y)))
        .flatMap { case (to, from) =>
          val outputFiles = jobs(from).outputsFiles
          val inputFiles = jobs(to).inputFiles
          outputFiles
            .filter(x => inputFiles.contains(x))
            .map(_.getName.stripSuffix(".gz").split("\\.").last)
            .distinct
            .map(x => Link(
              fromPort =
                if (jobs(from).configPath.nonEmpty)
                  "root" + jobs(from).configPath.mkString("/", "/", "/") + Job
                    .compressedName(from)
                    ._1 + s"/$x"
                else "root/" + Job.compressedName(from)._1 + s"/$x",
              toPort =
                if (jobs(to).configPath.nonEmpty)
                  "root" + jobs(to).configPath.mkString("/", "/", "/") + Job
                    .compressedName(to)
                    ._1 + s"/$x"
                else "root/" + Job.compressedName(to)._1 + s"/$x",
              linkType = Some(x),
              title = Some(x),
              description = Some(x)
            ))
        }
        .toArray
        .distinct

    def jobsToNode(jobs: List[Job], depth: Int = 0): Array[Node] = {
      val groups = jobs.groupBy(_.configPath.lift(depth))
      (groups
        .filter(_._1.isEmpty)
        .flatMap(_._2)
        .groupBy(x => Job.compressedName(x.name)._1)
        .map { j =>
          val inPorts = j._2
            .toList
            .flatMap(_.inputFiles)
            .map(_.getName.stripSuffix(".gz").split("\\.").last)
            .toArray.distinct.map(x => Port(name = x, title = Some(x)))
          val outPorts = j._2.toList.flatMap(_.outputsFiles).map(_.getName.stripSuffix(".gz").split("\\.").last).toArray.distinct.map(x => Port(name = x, title = Some(x)))

          Node(name = j._1,
            title = Some(j._1),
               inPorts = inPorts,
               outPorts = outPorts)
        } ++
        groups
          .filter(_._1.isDefined)
          .map(g =>
            Node(name = g._1.get, title = Some(g._1.get), children = jobsToNode(g._2, depth + 1)))).toArray
    }

    Run(
      name = runId,
      title = Some(runId),
      user = "biopet",
      root = Node(
        name = "root",
        title = Some("root"),
        children = jobsToNode(jobs.values.toList)
      ),
      links = links
    )
  }

  def publishRunToPim(pimRun: Run,
                      host: String,
                      deleteIfExist: Boolean = false)(
      implicit ws: AhcWSClient): Future[WSResponse] = {
    val runId = pimRun.name
    val checkRequest = ws
      .url(s"$host/api/runs/$runId")
      .withHeaders("Accept" -> "application/json",
                   "Content-Type" -> "application/json")
      .get()

    def postNew() =
      ws.url(s"$host/api/runs/")
        .withHeaders("Accept" -> "application/json",
                     "Content-Type" -> "application/json")
        .post(pimRun.toString)
        .map { r =>
          if (r.status != 200)
            throw new IllegalStateException(
              s"Post workflow did fail. Request: $r  Body: ${r.body}  Payload: ${pimRun.toString}")
          else r
        }

    checkRequest.flatMap { r =>
      if (r.status == 200) {
        if (deleteIfExist) {
          val delRequest = ws
            .url(s"$host/api/runs/$runId")
            .withHeaders("Accept" -> "application/json",
                         "Content-Type" -> "application/json")
            .delete()
          delRequest.flatMap { delR =>
            if (delR.status == 200) postNew()
            else
              throw new IllegalStateException(
                s"Delete workflow did fail. Request: $r  Body: ${r.body}")
          }
        } else
          throw new IllegalStateException(
            s"Run '$runId' already exist on pim instance")
      } else if (r.status == 404) postNew()
      else
        throw new IllegalStateException(
          s"Get workflow did fail. Request: $r  Body: ${r.body}")
    }
  }

  /** This publish the graph to a pim host */
  def publishCompressedGraphToPim(host: String,
                                  runId: String,
                                  deleteIfExist: Boolean = false)(
      implicit ws: AhcWSClient): Future[WSResponse] = {

    publishRunToPim(makeCompressedPimRun(runId), host, deleteIfExist).flatMap {
      r =>
        if (r.status != 200)
          throw new IllegalStateException(
            s"Post workflow did fail. Request: $r  Body: ${r.body}")
        val payload = jobs
          .map(
            job =>
              PimJob(
                name = job._1,
                title = Some(job._1),
                description = Some(job._1),
                node =
                  if (job._2.configPath.nonEmpty)
                    "root" + job._2.configPath
                      .mkString("/", "/", "/") + job._2.compressedName._1
                  else "root/" + job._2.compressedName._1,
                status = 0
              ).toString)
          .mkString("[", ",", "]")
        ws.url(s"$host/api/runs/$runId/jobs")
          .withHeaders("Accept" -> "application/json",
                       "Content-Type" -> "application/json")
          .post(payload)
          .map { r =>
            if (r.status == 200) logger.debug(r)
            else
              logger.warn(
                s"Post jobs did fail. Request: $r  Body: ${r.body}  payload: $payload")
            r
          }
    }
  }
}

object Deps {

  /** This will read a deps.json and returns it as a [[Deps]] class */
  def readDepsFile(depsFile: File): Deps = {
    val deps = Json.parse(Source.fromFile(depsFile).mkString)

    val jobs = (deps \ "jobs")
      .as[JsObject]
      .value
      .map(x => x._1 -> new Job(x._1, x._2.as[JsObject]))

    val files = (deps \ "files").as[JsArray]

    Deps(jobs.toMap, files.value.map(_.as[JsObject]).toArray)
  }
}
