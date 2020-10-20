import java.time.{LocalDate, LocalDateTime}

import models.{Authorization, PersonalData}
import org.specs2.mutable._

class QRCodeSpec extends Specification {

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
        "santé"
      )
      QRCode.buildContent(data, auth) must be equalTo ("Cree le: 02/01/2021 a 08h09;\nNom: Doe;\nPrenom: John;\n"
        + "Naissance: 03/02/1970 a Montélimar;\nAdresse: 1 rue de la Paix 75017 Paris;\n"
        + "Sortie: 04/03/2021 a 05:06;\nMotifs: sante")
    }
  }
}
