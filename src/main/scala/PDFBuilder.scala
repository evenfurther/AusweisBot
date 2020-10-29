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
        123,
        686,
        s"${data.firstName} ${data.lastName}",
        arial,
        11
      )
      addText(content, 123, 661, data.birthDateText, arial, 11)
      addText(content, 92, 638, data.birthPlace, arial, 11)
      addText(
        content,
        134,
        613,
        s"${data.street} ${data.zip} ${data.city}",
        arial,
        11
      )
      addText(content, 111, 226, data.city, arial, 11)
      auth.foreach { auth =>
        addText(content, 92, 200, dateText(auth.output), arial, 11)
        addText(content, 200, 201, hourText(auth.output), arial, 11)
        addText(content, 220, 201, minuteText(auth.output), arial, 11)
        addText(
          content,
          464,
          150,
          "Date de création:",
          PDType1Font.HELVETICA,
          7
        )
        addText(
          content,
          455,
          144,
          s"${dateText(auth.made)} à ${timeText(auth.made)}",
          PDType1Font.HELVETICA,
          7
        )
        auth.reasons.foreach { reason =>
          Authorization.reasons.get(reason).foreach {
            case (_, x, y) =>
              addText(content, x, y, "x", PDType1Font.HELVETICA, 19)
          }
        }
      }
      qrCodeImg.foreach(content.drawImage(_, 424.72f, 155, 100, 100))

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
      content.drawImage(qrCodeImg, 50, 491.88998f, 300, 300)
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
