import org.apache.commons.io.IOUtils

object GlobalConfig {

  val certificate = IOUtils.resourceToByteArray("/certificate.33362af4.pdf")

  var help: Option[String] = None
  var privacyPolicy: Option[String] = None

}
