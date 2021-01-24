import org.apache.commons.io.IOUtils

object GlobalConfig {

  val certificate = IOUtils.resourceToByteArray("/certificate.8822af29.pdf")

  var help: Option[String] = None
  var privacyPolicy: Option[String] = None

}
