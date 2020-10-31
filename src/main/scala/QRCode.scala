import java.io.ByteArrayOutputStream

import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.{BarcodeFormat, EncodeHintType, MultiFormatWriter}
import models.{Authorization, PersonalData}

import scala.collection.JavaConverters._

/**
  * QR-code object with correction level M.
  *
  * @param width the desired width
  * @param height the desired height
  * @param text the text to put into the QR code
  */
class QRCode(width: Int, height: Int, text: String) {

  private[this] val matrix = {
    val hintMap = Map(
      EncodeHintType.ERROR_CORRECTION -> ErrorCorrectionLevel.M,
      EncodeHintType.MARGIN -> -1
    )
    new MultiFormatWriter().encode(
      text,
      BarcodeFormat.QR_CODE,
      width,
      height,
      hintMap.asJava
    )
  }

  /**
    * Return the bytes making up the PNG for this QR code.
    *
    * @return the PNG data
    */
  def pngBytes: Array[Byte] = {
    val stream = new ByteArrayOutputStream
    MatrixToImageWriter.writeToStream(matrix, "png", stream)
    stream.toByteArray
  }

}

object QRCode {

  /**
    * Build a QR-code.
    *
    * @param width the desired width
    * @param height the desired height
    * @param data the user data
    * @param auth the output from home information
    * @return a QR-code with correction level M containing the requested data
    */
  def apply(
      width: Int,
      height: Int,
      data: PersonalData,
      auth: Authorization
  ): QRCode = {
    new QRCode(width, height, buildContent(data, auth))
  }

  def buildContent(data: PersonalData, auth: Authorization): String = {
    import utils._
    val reasons =
      Authorization.orderedCanonicalValidReasons(auth.reasons).mkString(", ")
    s"""Cree le: ${dateText(auth.made)} a ${timeText(auth.made)
         .replace(':', 'h')}
       |Nom: ${data.lastName}
       |Prenom: ${data.firstName}
       |Naissance: ${data.birthDateText} a ${data.birthPlace}
       |Adresse: ${data.street} ${data.zip} ${data.city}
       |Sortie: ${dateText(auth.output)} a ${timeText(auth.output)}
       |Motifs: $reasons""".stripMargin.linesIterator.mkString(";\n ")
  }
}
