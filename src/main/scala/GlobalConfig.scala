import org.apache.commons.io.IOUtils

object GlobalConfig {

  val certificate = IOUtils.resourceToByteArray("/certificate.d3f46166.pdf")

  var help: Option[String] = None
  var privacyPolicy: Option[String] = None

}
