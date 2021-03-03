import org.specs2.mutable._
import models.PersonalDataBuilder
import java.time.LocalDate

class PersonalDataBuilderSpec extends Specification {

  "parseBirthDate" should {
    "accept complete dates regardless of the year" in {
      PersonalDataBuilder.parseBirthDate("17/4/1870") must be equalTo (LocalDate
        .of(
          1870,
          4,
          17))
      PersonalDataBuilder.parseBirthDate("17/4/2030") must be equalTo (LocalDate
        .of(
          2030,
          4,
          17))
    }

    "complete two-digits years" in {
      PersonalDataBuilder.parseBirthDate("1/2/3") must be equalTo (LocalDate
        .of(2003, 2, 1))
      PersonalDataBuilder.parseBirthDate("17/1/78") must be equalTo (LocalDate
        .of(1978, 1, 17))
    }

    "fail on invalid dates" in {
      PersonalDataBuilder.parseBirthDate("1/17/78") must throwA[RuntimeException]
      PersonalDataBuilder.parseBirthDate("29/2/1900") must throwA[RuntimeException]
    }
  }

  "checkBirthDate" should {
    "reject invalid dates" in {
      PersonalDataBuilder.checkBirthDate("9/13/1990") must beSome
      PersonalDataBuilder.checkBirthDate("29/2/1900") must beSome
    }

    "accept plausible dates" in {
      PersonalDataBuilder.checkBirthDate("29/2/2000") must beNone
      PersonalDataBuilder.checkBirthDate("13/9/1922") must beNone
      PersonalDataBuilder.checkBirthDate("13/9/1967") must beNone
      PersonalDataBuilder.checkBirthDate("13/9/67") must beNone
      PersonalDataBuilder.checkBirthDate("22/3/2008") must beNone
      PersonalDataBuilder.checkBirthDate("22/3/8") must beNone
    }

    "reject dates in the future" in {
      PersonalDataBuilder.checkBirthDate("13/9/2030") must beSome
    }

    "reject dates too far in the past" in {
      PersonalDataBuilder.checkBirthDate("13/9/1890") must beSome
    }
  }

}
