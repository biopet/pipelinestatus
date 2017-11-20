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
    def links: Array[Link] = this
      .jobs.flatMap(x => x._2.dependsOnJobs.map(y => (x._1, y)))
      .map(x =>
        Link(
          fromPort = "root" + jobs(x._2).configPath.mkString("/", "/", "/") + x._2 + "/output",
          toPort = "root" + jobs(x._1).configPath.mkString("/", "/", "/") + x._1 + "/input"))
      .toArray

    def jobsToNode(jobs: List[Job], depth: Int = 0): Array[Node] = {
      val groups = jobs.groupBy(_.configPath.lift(depth))
      (groups.filter(_._1.isEmpty).flatMap(_._2).map(j => Node(name = j.name, inPorts = Array(Port(name = "input")), outPorts = Array(Port(name = "output")))) ++
        groups.filter(_._1.isDefined).map(g => Node(name = g._1.get, children = jobsToNode(g._2, depth + 1)))).toArray
    }

    Run(
      name = runId,
      user = "biopet",
      root = Node(
        name = "root",
        children = jobsToNode(jobs.values.toList)
      ),
      links = links
    )
  }

  def publishGraphToPim(host: String, runId: String, deleteIfExist: Boolean = false)(
    implicit ws: AhcWSClient): Future[WSResponse] = {

    publishRunToPim(makePimRun(runId), host, deleteIfExist).flatMap { r =>
      if (r.status != 200) throw new IllegalStateException(s"Post workflow did fail. Request: $r  Body: ${r.body}")
      val payload = jobs.map(job => PimJob(name = job._1, title = Some(job._1), description = Some(job._1),
        node = "root" + job._2.configPath.mkString("/","/","/") + job._1,
        status = 0).toString).mkString("[", ",", "]")
      ws.url(s"$host/api/runs/$runId/jobs")
        .withHeaders("Accept" -> "application/json",
          "Content-Type" -> "application/json")
        .post(payload)
        .map { r =>
          if (r.status == 200) logger.debug(r)
          else logger.warn(s"Post jobs did fail. Request: $r  Body: ${r.body}  payload: $payload")
          r
        }
    }
  }

  def makeCompressedPimRun(runId: String): Run = {
    val links: List[Link] = this
      .compressOnType()
      .flatMap(x =>
        x._2.map(y => Link(fromPort = "root/" + y + "/output", toPort = "root/" + x._1 + "/input")))
      .toList

    Run(
      name = runId,
      user = "biopet",
      root = Node(
        name = "root",
        children = this
          .compressOnType()
          .map(
            x =>
              Node(
                name = x._1,
                inPorts = Array(Port(name = "input")),
                outPorts = Array(Port(name = "output"))
              ))
          .toArray
      ),
      links = links.toArray
    )
  }

  def publishRunToPim(pimRun: Run, host: String, deleteIfExist: Boolean = false)(
          implicit ws: AhcWSClient): Future[WSResponse] = {
    val runId = pimRun.name
    val checkRequest = ws.url(s"$host/api/runs/$runId")
      .withHeaders("Accept" -> "application/json",
        "Content-Type" -> "application/json").get()

    def postNew() = ws
        .url(s"$host/api/runs/")
        .withHeaders("Accept" -> "application/json",
          "Content-Type" -> "application/json")
        .post(pimRun.toString).map { r =>
        if (r.status != 200) throw new IllegalStateException(s"Post workflow did fail. Request: $r  Body: ${r.body}  Payload: ${pimRun.toString}")
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
            else throw new IllegalStateException(s"Delete workflow did fail. Request: $r  Body: ${r.body}")
          }
        } else throw new IllegalStateException(s"Run '$runId' already exist on pim instance")
      } else if (r.status == 404) postNew()
      else throw new IllegalStateException(s"Get workflow did fail. Request: $r  Body: ${r.body}")
    }
  }

  /** This publish the graph to a pim host */
  def publishCompressedGraphToPim(host: String, runId: String, deleteIfExist: Boolean = false)(
      implicit ws: AhcWSClient): Future[WSResponse] = {

    publishRunToPim(makeCompressedPimRun(runId), host, deleteIfExist).flatMap { r =>
      if (r.status != 200) throw new IllegalStateException(s"Post workflow did fail. Request: $r  Body: ${r.body}")
      val payload = jobs.map(job => PimJob(name = job._1, title = Some(job._1), description = Some(job._1),
        node = "root/" + job._2.compressedName._1,
        status = 0).toString).mkString("[", ",", "]")
      ws.url(s"$host/api/runs/$runId/jobs")
        .withHeaders("Accept" -> "application/json",
          "Content-Type" -> "application/json")
        .post(payload)
        .map { r =>
          if (r.status == 200) logger.debug(r)
          else logger.warn(s"Post jobs did fail. Request: $r  Body: ${r.body}  payload: $payload")
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
