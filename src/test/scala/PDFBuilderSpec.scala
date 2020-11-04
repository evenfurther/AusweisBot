import org.specs2.mutable._
import models.{Authorization, PersonalData}
import java.time.{LocalDate, LocalDateTime}
import org.apache.pdfbox.pdmodel.PDDocument
import org.specs2.matcher.Matcher

class PDFBuilderSpec extends Specification {

  val data = PersonalData(
    "Doe",
    "John",
    LocalDate.of(2001, 4, 17),
    "Lisbonne (Portugal)",
    "1 rue de la Paix",
    "91120",
    "Palaiseau"
  )

  val auth = Authorization(
    LocalDateTime.of(2020, 11, 3, 17, 4),
    LocalDateTime.of(2020, 11, 3, 17, 3),
    Seq("travail")
  )

  val beAValidPDF: Matcher[Array[Byte]] = { b: Array[Byte] =>
    try {
      PDDocument.load(b).close()
      success
    } catch {
      case _: Throwable => failure
    }
  }

  "buildPDF" should {

    "not throw on accented characters absent in font" in {
      val d = data.copy(birthPlace = "Focșani (Roumanie)")
      PDFBuilder.buildPDF(d, Some(auth)) should beAValidPDF
    }

    "throw on non-textual characters such as emojis" in {
      val d = data.copy(birthPlace = "😊")
      PDFBuilder.buildPDF(d, Some(auth)) should throwAn[
        IllegalArgumentException
      ]
    }

    "return an empty model if requested" in {
      PDFBuilder.buildPDF(data, None) should beAValidPDF
    }
  }
}
