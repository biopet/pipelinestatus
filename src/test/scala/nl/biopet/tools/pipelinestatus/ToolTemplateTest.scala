package nl.biopet.tools.pipelinestatus

import nl.biopet.test.BiopetTest
import org.testng.annotations.Test

class PipelineStatusTest extends BiopetTest {
  @Test
  def testNoArgs(): Unit = {
    intercept[IllegalArgumentException] {
      PipelineStatus.main(Array())
    }
  }
}
