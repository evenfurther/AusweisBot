import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import models._
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.{PDFont, PDType0Font, PDType1Font}
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage, PDPageContentStream}
import scala.util.Try

class PDFBuilder(model: Array[Byte]) {

  /**
    * Build a PDF document from personal data and optional output from home information.
    *
    * @param data the user data
    * @param auth if defined, the output from home information to use, otherwise an
    *             pre-filled to-fill-and-sign certificate will be returned
    * @return the PDF document content
    */
  def buildPDF(data: PersonalData, auth: Option[Authorization]): Array[Byte] = {
    import utils._
    val doc = PDDocument.load(model)
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
        119,
        696,
        s"${data.firstName} ${data.lastName}",
        11
      )
      addText(content, 119, 674, data.birthDateText, 11)
      addText(content, 297, 674, data.birthPlace, 11)
      addText(
        content,
        133,
        652,
        s"${data.street} ${data.zip} ${data.city}",
        11
      )
      addText(content, 105, 177, data.city, 11)
      auth.foreach { auth =>
        addText(content, 91, 153, dateText(auth.output), 11)
        addText(content, 264, 153, timeText(auth.output), 11)
        auth.reasons.foreach { reason =>
          Authorization.reasons.get(reason).foreach {
            case (_, y, _) =>
              addText(content, 84, y, "x", 18)
          }
        }
      }
      qrCodeImg.foreach(content.drawImage(_, 439, 100, 92, 92))

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
      content.drawImage(qrCodeImg, 50, 491.89f, 300, 300)
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
    content.showText(text)
    content.endText()
  }

}

object PDFBuilder {

  case class BuildPDF(
      data: PersonalData,
      auth: Option[Authorization],
      replyTo: ActorRef[Try[Array[Byte]]]
  )

  /**
    * Make a PDF builder.
    *
    * @param model the original PDF to start with
    * @return a behavior to serially build PDF documents
    */
  def makeActor(model: Array[Byte]): Behavior[BuildPDF] = Behaviors.setup {
    implicit context =>
      val pdfBuilder = new PDFBuilder(model)
      Behaviors.receiveMessage {
        case BuildPDF(data, auth, replyTo) =>
          replyTo ! Try { pdfBuilder.buildPDF(data, auth) }
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

}
