package nl.biopet.tools.pipelinestatus

import nl.biopet.utils.test.tools.ToolTest
import org.testng.annotations.Test

class PipelineStatusTest extends ToolTest[Args] {

  def toolCommand: PipelineStatus.type = PipelineStatus

  @Test
  def testNoArgs(): Unit = {
    intercept[IllegalArgumentException] {
      PipelineStatus.main(Array())
    }
  }
}
