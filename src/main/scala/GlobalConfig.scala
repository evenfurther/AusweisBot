import org.apache.commons.io.IOUtils

object GlobalConfig {

  val certificate = IOUtils.resourceToByteArray("/certificate.0eed39bb.pdf")

  var help: Option[String] = None
  var privacyPolicy: Option[String] = None

}
