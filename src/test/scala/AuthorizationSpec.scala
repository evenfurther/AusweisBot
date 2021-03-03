import models.Authorization
import org.specs2.mutable._

class AuthorizationSpec extends Specification {

  "unifyValidReasons" should {
    "remove the duplicates by keeping the first one only" in {
      Authorization.unifyValidReasons(
        Seq("sante", "soins", "famille")) must be equalTo (Seq(
          "sante",
          "famille"))
      Authorization.unifyValidReasons(
        Seq("sante", "famille", "soins")) must be equalTo (Seq(
          "sante",
          "famille"))
    }

    "throw an exception on invalid reasons" in {
      Authorization.unifyValidReasons(Seq("invalid")) must throwA[RuntimeException]
    }
  }

  "orderedCanonicalValidReasons" should {
    "work as expected" in {
      Authorization.orderedCanonicalValidReasons(
        Seq("santé", "mission", "famille", "travail")) must be equalTo (Seq(
          "travail",
          "sante",
          "famille",
          "missions"))
      Authorization.orderedCanonicalValidReasons(
        Seq("mission", "santé", "famille", "travail")) must be equalTo (Seq(
          "travail",
          "sante",
          "famille",
          "missions"))
    }
  }

}
