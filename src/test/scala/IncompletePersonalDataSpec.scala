import org.specs2.mutable._
import models.IncompletePersonalData
import java.time.LocalDate

class IncompletePersonalDataSpec extends Specification {

  "parseBirthDate" should {
    "accept complete dates regardless of the year" in {
      IncompletePersonalData.parseBirthDate("17/4/1870") must be equalTo (LocalDate
        .of(
          1870,
          4,
          17
        ))
      IncompletePersonalData.parseBirthDate("17/4/2030") must be equalTo (LocalDate
        .of(
          2030,
          4,
          17
        ))
    }

    "complete two-digits years" in {
      IncompletePersonalData.parseBirthDate("1/2/3") must be equalTo (LocalDate
        .of(2003, 2, 1))
      IncompletePersonalData.parseBirthDate("17/1/78") must be equalTo (LocalDate
        .of(1978, 1, 17))
    }

    "fail on invalid dates" in {
      IncompletePersonalData.parseBirthDate("1/17/78") must throwA[
        RuntimeException
      ]
      IncompletePersonalData.parseBirthDate("29/2/1900") must throwA[
        RuntimeException
      ]
    }
  }

  "checkBirthDate" should {
    "reject invalid dates" in {
      IncompletePersonalData.checkBirthDate("9/13/1990") must beSome
      IncompletePersonalData.checkBirthDate("29/2/1900") must beSome
    }

    "accept plausible dates" in {
      IncompletePersonalData.checkBirthDate("29/2/2000") must beNone
      IncompletePersonalData.checkBirthDate("13/9/1922") must beNone
      IncompletePersonalData.checkBirthDate("13/9/1967") must beNone
      IncompletePersonalData.checkBirthDate("13/9/67") must beNone
      IncompletePersonalData.checkBirthDate("22/3/2008") must beNone
      IncompletePersonalData.checkBirthDate("22/3/8") must beNone
    }

    "reject dates in the future" in {
      IncompletePersonalData.checkBirthDate("13/9/2030") must beSome
    }

    "reject dates too far in the past" in {
      IncompletePersonalData.checkBirthDate("13/9/1890") must beSome
    }
  }

}
