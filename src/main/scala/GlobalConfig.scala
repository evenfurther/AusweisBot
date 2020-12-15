import org.apache.commons.io.IOUtils

object GlobalConfig {

  val certificate = IOUtils.resourceToByteArray("/certificate.ee43ab99.pdf")

  var help: Option[String] = None
  var privacyPolicy: Option[String] = None

}
