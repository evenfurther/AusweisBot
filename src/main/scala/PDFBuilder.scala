import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import models._
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.{PDFont, PDType0Font, PDType1Font}
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage, PDPageContentStream}

class PDFBuilder(model: Array[Byte], arialFont: Array[Byte]) {

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
    val arial = PDType0Font.load(doc, new ByteArrayInputStream(arialFont))
    val qrCodeImg = auth.map { auth =>
      val qrCode = QRCode(220, 220, data, auth)
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
        669,
        s"${data.firstName} ${data.lastName}",
        arial,
        11
      )
      addText(content, 119, 646, data.birthDateText, arial, 11)
      addText(content, 312, 646, data.birthPlace, arial, 11)
      addText(
        content,
        133,
        622,
        data.street,
        arial,
        11
      )
      addText(content, 133, 609, s"${data.zip} ${data.city}", arial, 11)
      addText(content, 105, 168, data.city, arial, 11)
      auth.foreach { auth =>
        addText(content, 91, 146, dateText(auth.output), arial, 11)
        addText(content, 312, 146, timeText(auth.output), arial, 11)
        Authorization.coordinates(auth.reason).foreach {
          case (x, y) =>
            addText(content, x, y, "x", PDType1Font.HELVETICA, 18)
        }
      }
      qrCodeImg.foreach(content.drawImage(_, 424.72f, 122f, 92, 92))

      // Add a "Signature" field if the form is not fully filled and requires manual filling
      // and signing. Note that this is not the exact font used in the original paper certificate
      // but it is close enough.
      if (auth.isEmpty) {
        addText(content, 72, 153, "Signature :", PDType1Font.HELVETICA, 11)
      }

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
      content.drawImage(qrCodeImg, 50f, 491.88998f, 300, 300)
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
      font: PDFont,
      size: Int
  ) {
    content.beginText()
    content.setFont(font, size)
    content.newLineAtOffset(x, y)
    content.showText(text)
    content.endText()
  }

}

object PDFBuilder {

  case class BuildPDF(
      data: PersonalData,
      auth: Option[Authorization],
      replyTo: ActorRef[Array[Byte]]
  )

  /**
    * Make a PDF builder.
    *
    * @param model the original PDF to start with
    * @param arialFont the arial font data
    * @return a behavior to serially build PDF documents
    */
  def makeActor(
      model: Array[Byte],
      arialFont: Array[Byte]
  ): Behavior[BuildPDF] = Behaviors.setup { implicit context =>
    val pdfBuilder = new PDFBuilder(model, arialFont)
    Behaviors.receiveMessage {
      case BuildPDF(data, auth, replyTo) =>
        replyTo ! pdfBuilder.buildPDF(data, auth)
        Behaviors.same
    }
  }

}
