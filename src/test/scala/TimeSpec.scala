import java.time.{LocalDate, LocalTime}

import org.specs2.mutable._

class TimeSpec extends Specification {

  def dayOffset(outputHour: Int, currentHour: Int): Option[Int] = {
    val refDate = LocalDate.of(2020, 4, 10)
    val now = LocalTime.of(currentHour, 0).atDate(refDate)
    val output = ChatterBot.addCredibleDate(LocalTime.of(outputHour, 0), now)
    output.map(_.getDayOfYear - now.getDayOfYear)
  }

  "addCredibleTime while it is 21:00" should {
    "determine that 15:00 is on the same day" in {
      dayOffset(15, 21) should be equalTo Some(0)
    }

    "determine that 19:00 is on the same day" in {
      dayOffset(19, 21) should be equalTo Some(0)
    }

    "determine that 2:00 is the day after" in {
      dayOffset(2, 21) should be equalTo Some(1)
    }

    "refuse to choose a day for 9:00" in {
      dayOffset(9, 21) should be equalTo None
    }
  }

  "addCredibleTime while it is 4:00" should {
    "determine that 9:00 is on the same day" in {
      dayOffset(9, 4) should be equalTo Some(0)
    }

    "determine that 1:00 is on the same day" in {
      dayOffset(1, 4) should be equalTo Some(0)
    }

    "determine that 23:00 is the day before" in {
      dayOffset(23, 4) should be equalTo Some(-1)
    }

    "refuse to choose a day for 16:00" in {
      dayOffset(16, 4) should be equalTo None
    }
  }
}
