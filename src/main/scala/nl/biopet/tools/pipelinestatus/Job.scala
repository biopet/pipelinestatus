package nl.biopet.tools.pipelinestatus

import java.io.File

import play.api.libs.json.{JsArray, JsObject}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex

/**
  * This class can store a single job from a deps.json
  *
  * Created by pjvanthof on 24/06/2017.
  */
class Job(val name: String, map: JsObject) {

  /** When true this job was done at the moment of the deps.json creation */
  def doneAtStart: Boolean = (map \ "done_at_start").get.as[Boolean]

  /** If one of this files exist the job is marked as failed */
  def failFiles: List[File] =
    (map \ "fail_files").get.as[List[String]].map(new File(_))

  /** If all of this files exist the job is marked as done */
  def doneFiles: List[File] =
    (map \ "done_files").get.as[List[String]].map(new File(_))

  /** Returns a list of jobs that depends on this job */
  def outputUsedByJobs: List[String] =
    (map \ "output_used_by_jobs").get.as[List[String]]

  /** Returns a list of job where this job depends on */
  def dependsOnJobs: List[String] =
    (map \ "depends_on_jobs").get.as[List[String]]

  /** Location of the stdout file of this job */
  def stdoutFile = new File((map \ "stdout_file").as[String])

  /** All output files of this job */
  def outputsFiles: List[File] =
    (map \ "outputs").get.as[List[String]].map(new File(_))

  /** All input files of this job */
  def inputFiles: List[File] =
    (map \ "inputs").get.as[List[String]].map(new File(_))

  /** When true this job is marked as a main job in the graph */
  def mainJob: Boolean = (map \ "main_job").get.as[Boolean]

  /** When true this job is marked as a intermediate job */
  def intermediate: Boolean = (map \ "intermediate").get.as[Boolean]

  /** Return a [[Future[Boolean]] to check if the job is done */
  def isDone: Future[Boolean] = Future { doneFiles.forall(_.exists()) }

  /** Return a [[Future[Boolean]] to check if the job is failed */
  def isFailed: Future[Boolean] = Future { failFiles.exists(_.exists()) }

  /** Returns the compressed name of this job */
  def compressedName: (String, Int) = Job.compressedName(name)

  /** Getting config path of job */
  def configPath: List[String] = {
    (map \ "config_path").getOrElse(JsArray.empty).as[List[String]]
  }
}

object Job {
  val numberRegex: Regex = """(.*)_(\d*)$""".r

  /** This splits a job name from it's id */
  def compressedName(jobName: String): (String, Int) = jobName match {
    case Job.numberRegex(name, number) => (name, number.toInt)
  }

}
