import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import models._
import org.apache.commons.lang3.StringUtils
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.{PDFont, PDType0Font, PDType1Font}
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage, PDPageContentStream}
import scala.util.Try

object PDFBuilder {

  /** Build a PDF document from personal data and optional output from home
    * information.
    *
    * @param data
    *   the user data
    * @param auth
    *   if defined, the output from home information to use, otherwise an
    *   pre-filled to-fill-and-sign certificate will be returned
    * @return
    *   the PDF document content
    */
  def buildPDF(data: PersonalData, auth: Option[Authorization]): Array[Byte] = {
    import utils._
    val doc = PDDocument.load(GlobalConfig.certificate)
    PDFBuilder.addMetadata(doc)
    val qrCodeImg = auth.map { auth =>
      val qrCode = QRCode(300, data, auth)
      PDImageXObject.createFromByteArray(doc, qrCode.pngBytes, "qrcode.png")
    }

    {
      val first_page = doc.getPage(0)

      val content = new PDPageContentStream(
        doc,
        first_page,
        PDPageContentStream.AppendMode.APPEND,
        true,
        true
      )
      addText(
        content,
        144,
        705,
        StringUtils.stripAccents(s"${data.firstName} ${data.lastName}"),
        11
      )
      addText(content, 144, 684, data.birthDateText, 11)
      addText(content, 310, 684, StringUtils.stripAccents(data.birthPlace), 11)
      addText(
        content,
        148,
        665,
        StringUtils.stripAccents(s"${data.street} ${data.zip} ${data.city}"),
        11
      )
      addText(content, 103, 99, data.city, 11)
      auth.foreach { auth =>
        addText(content, 91, 83, dateText(auth.output), 11)
        addText(content, 310, 83, timeText(auth.output), 11)
        auth.reasons.foreach { reason =>
          Authorization.reasons.get(reason).foreach { case (_, y, _, _) =>
            addText(content, 72, y, "x", 12)
          }
        }
      }
      qrCodeImg.foreach(content.drawImage(_, 488.32f, 660, 82, 82))

      content.close()
    }

    qrCodeImg.foreach { qrCodeImg =>
      val second_page = new PDPage(PDRectangle.A4)
      doc.addPage(second_page)
      val content = new PDPageContentStream(
        doc,
        second_page,
        PDPageContentStream.AppendMode.APPEND,
        true,
        true
      )
      content.drawImage(qrCodeImg, 50, 451.89f, 300, 300)
      content.close()
    }

    val result = new ByteArrayOutputStream
    doc.save(result)
    doc.close()

    result.toByteArray
  }

  private def addText(
      content: PDPageContentStream,
      x: Float,
      y: Float,
      text: String,
      size: Int
  ) {
    content.beginText()
    content.setFont(PDType1Font.HELVETICA, size)
    content.newLineAtOffset(x, y)
    // As of 2020-03-01, all accents are supposed to be stripped, but this
    // has not always been the case in the governement generator and may
    // return, so let's keep this in place.
    try {
      content.showText(text)
    } catch {
      case _: IllegalArgumentException =>
        try {
          content.showText(StringUtils.stripAccents(text))
        } catch {
          case _: IllegalArgumentException =>
            throw NonPrintable(text)
        }
    }
    content.endText()
  }

  case class BuildPDF(
      data: PersonalData,
      auth: Option[Authorization],
      replyTo: ActorRef[Try[Array[Byte]]]
  )

  /** Make a PDF builder.
    *
    * @return
    *   a behavior to serially build PDF documents
    */
  def makeActor(): Behavior[BuildPDF] = Behaviors.setup { implicit context =>
    Behaviors.receiveMessage { case BuildPDF(data, auth, replyTo) =>
      replyTo ! Try { buildPDF(data, auth) }
      Behaviors.same
    }
  }

  private def addMetadata(doc: PDDocument) {
    val info = doc.getDocumentInformation()
    info.setTitle("COVID-19 - Déclaration de déplacement")
    info.setSubject("Attestation de déplacement dérogatoire")
    info.setKeywords(
      "covid19,covid-19,attestation,déclaration,déplacement,officielle,gouvernement"
    )
    info.setProducer("DNUM/SDIT")
    info.setCreator("")
    info.setAuthor("Ministère de l'intérieur")
    doc.setVersion(1.7f)
  }

  case class NonPrintable(s: String) extends Exception(s)

}
