import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

import io.nayuki.qrcodegen.{QrCode, QrSegment}
import models.{Authorization, PersonalData}
import org.apache.commons.lang3.StringUtils

import scala.collection.JavaConverters._

/** QR-code object with correction level M.
  *
  * @param size the desired side size
  * @param text the text to put into the QR code
  */
class QRCode(size: Int, text: String) {

  private[this] val matrix = QrCode.encodeText(text, QrCode.Ecc.MEDIUM)

  /** Return the bytes making up the PNG for this QR code.
    *
    * @return the PNG data
    */
  def pngBytes: Array[Byte] = {
    val scale = (size - 2) / matrix.size
    val img = matrix.toImage(scale, 1)
    val stream = new ByteArrayOutputStream
    ImageIO.write(img, "png", stream)
    stream.toByteArray
  }

}

object QRCode {

  /** Build a QR-code.
    *
    * @param size the desired side size
    * @param data the user data
    * @param auth the output from home information
    * @return a QR-code with correction level M containing the requested data
    */
  def apply(
      size: Int,
      data: PersonalData,
      auth: Authorization
  ): QRCode =
    new QRCode(size, buildContent(data, auth))

  def buildContent(data: PersonalData, auth: Authorization): String = {
    import utils._
    val reasons =
      Authorization.orderedCanonicalValidReasons(auth.reasons).mkString(", ")
    val accented = s"""Cree le: ${dateText(auth.made)} a ${timeText(auth.made)
      .replace(':', 'h')}
       |Nom: ${data.lastName}
       |Prenom: ${data.firstName}
       |Naissance: ${data.birthDateText} a ${data.birthPlace}
       |Adresse: ${data.street} ${data.zip} ${data.city}
       |Sortie: ${dateText(auth.output)} a ${timeText(auth.output)}
       |Motifs: $reasons""".stripMargin.linesIterator.mkString(";\n") + ";\n"
    StringUtils.stripAccents(accented)
  }
}
