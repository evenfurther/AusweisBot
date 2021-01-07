import org.apache.commons.io.IOUtils

object GlobalConfig {

  val certificate = IOUtils.resourceToByteArray("/certificate.5135cadd.pdf")

  var help: Option[String] = None
  var privacyPolicy: Option[String] = None

}
