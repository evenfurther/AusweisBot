import java.time.{LocalDate, LocalDateTime}

import models.{Authorization, PersonalData}
import org.specs2.mutable._
import org.apache.commons.io.IOUtils
import java.io.FileOutputStream
import com.google.zxing.BinaryBitmap
import java.awt.image.BufferedImage
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.{MultiFormatReader, Result}

class QRCodeSpec extends Specification {

  def decodeOfficial(): Result = {
      val reader = new com.google.zxing.MultiFormatReader
      val image = javax.imageio.ImageIO.read(IOUtils.resourceToURL("/official-qrcode.png"))
      val source = new BufferedImageLuminanceSource(image)
      val binarizer = new HybridBinarizer(source)
      val binaryBitmap = new BinaryBitmap(binarizer)
      (new MultiFormatReader).decode(binaryBitmap)
  }

  "buildContent" should {
    "generate the expected content" in {
      val data = PersonalData(
        "Doe",
        "John",
        LocalDate.of(1970, 2, 3),
        "Montélimar",
        "1 rue de la Paix",
        "75017",
        "Paris"
      )
      val auth = Authorization(
        LocalDateTime.of(2021, 3, 4, 5, 6),
        LocalDateTime.of(2021, 1, 2, 8, 9),
        Seq("santé")
      )
      QRCode.buildContent(data, auth) must be equalTo ("Cree le: 02/01/2021 a 08h09;\n Nom: Doe;\n Prenom: John;\n "
        + "Naissance: 03/02/1970 a Montélimar;\n Adresse: 1 rue de la Paix 75017 Paris;\n "
        + "Sortie: 04/03/2021 a 05:06;\n Motifs: sante")
    }

    "generate a content similar to the official site" in {
      val data = PersonalData(
        "Doe",
        "John",
        LocalDate.of(1984, 3, 1),
        "Paris 2",
        "1 rue de la Paix",
        "72635",
        "Bézou-sur-Cher"
      )
      val auth = Authorization(
        LocalDateTime.of(2020, 11, 1, 4, 5),
        LocalDateTime.of(2020, 10, 31, 11, 13),
        Seq("travail")
      )
      QRCode.buildContent(data, auth) must be equalTo(decodeOfficial.getText)
    }
  }
}
