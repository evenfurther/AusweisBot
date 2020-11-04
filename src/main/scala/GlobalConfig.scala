import org.apache.commons.io.IOUtils

object GlobalConfig {

  val certificate = IOUtils.resourceToByteArray("/certificate.1e3570bc.pdf")

  var help: Option[String] = None
  var privacyPolicy: Option[String] = None

}
