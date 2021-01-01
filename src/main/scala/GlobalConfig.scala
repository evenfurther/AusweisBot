import org.apache.commons.io.IOUtils

object GlobalConfig {

  val certificate = IOUtils.resourceToByteArray("/certificate.b4b7cb9d.pdf")

  var help: Option[String] = None
  var privacyPolicy: Option[String] = None

}
