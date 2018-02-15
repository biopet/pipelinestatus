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

import java.io.File

import nl.biopet.test.BiopetTest
import org.testng.annotations.Test

class DepsTest extends BiopetTest {
  @Test
  def testReading(): Unit = {
    val deps = Deps.readDepsFile(resourceFile("/deps.json"))

    val job1 = deps.jobs("job_1")
    job1.name shouldBe "job_1"
    job1.inputFiles shouldBe Nil
    job1.outputsFiles shouldBe Nil
    job1.stdoutFile shouldBe new File("stdout1")
    job1.doneAtStart shouldBe true
    job1.failFiles shouldBe List(new File(".stdout1.fail"))
    job1.doneFiles shouldBe List(new File(".stdout1.done"))
    job1.dependsOnJobs shouldBe Nil
    job1.outputUsedByJobs shouldBe List("job_2")
    job1.mainJob shouldBe true
    job1.intermediate shouldBe false
    job1.compressedName shouldBe ("job", 1)
    job1.configPath shouldBe Nil

    val job2 = deps.jobs("job_2")
    job2.name shouldBe "job_2"
    job2.inputFiles shouldBe Nil
    job2.outputsFiles shouldBe Nil
    job2.stdoutFile shouldBe new File("stdout2")
    job2.doneAtStart shouldBe false
    job2.failFiles shouldBe List(new File(".stdout2.fail"))
    job2.doneFiles shouldBe List(new File(".stdout2.done"))
    job2.dependsOnJobs shouldBe List("job_1")
    job2.outputUsedByJobs shouldBe List("jobX_3")
    job2.mainJob shouldBe false
    job2.intermediate shouldBe true
    job2.compressedName shouldBe ("job", 2)
    job2.configPath shouldBe Nil

    val job3 = deps.jobs("jobX_3")
    job3.name shouldBe "jobX_3"
    job3.inputFiles shouldBe Nil
    job3.outputsFiles shouldBe Nil
    job3.stdoutFile shouldBe new File("stdout3")
    job3.doneAtStart shouldBe false
    job3.failFiles shouldBe List(new File(".stdout3.fail"))
    job3.doneFiles shouldBe List(new File(".stdout3.done"))
    job3.dependsOnJobs shouldBe List("job_2")
    job3.outputUsedByJobs shouldBe Nil
    job3.mainJob shouldBe true
    job3.intermediate shouldBe false
    job3.compressedName shouldBe ("jobX", 3)
    job3.configPath shouldBe Nil

    val compress = deps.compressOnType()
    compress.keySet shouldBe Set("job", "jobX")
    compress("job") shouldBe List("job")
    compress("jobX") shouldBe List("job")

    val compressMain = deps.compressOnType(true)
    compressMain.keySet shouldBe Set("job", "jobX")
    compressMain("job") shouldBe Nil
    compressMain("jobX") shouldBe List("job")

    deps.getMainDeps shouldBe Map("job_1" -> List(), "jobX_3" -> List("job_1"))
  }

  @Test
  def testConfigPath(): Unit = {
    val deps = Deps.readDepsFile(resourceFile("/deps.with_config_path.json"))

    val job1 = deps.jobs("job_1")
    job1.configPath shouldBe List("rootPipeline")

    val job2 = deps.jobs("job_2")
    job2.configPath shouldBe List("rootPipeline", "subPipeline")

    val job3 = deps.jobs("jobX_3")
    job3.configPath shouldBe List("rootPipeline", "subPipeline")
  }
}
