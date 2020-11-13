import models.Authorization
import org.specs2.mutable._

class AuthorizationSpec extends Specification {

  "unifyValidReasons" should {
    "remove the duplicates by keeping the first one only" in {
      Authorization.unifyValidReasons(
        Seq("sport", "famille", "promenade")
      ) must be equalTo (Seq(
        "sport_animaux",
        "famille"
      ))
      Authorization.unifyValidReasons(
        Seq("promenade", "famille", "sport")
      ) must be equalTo (Seq(
        "sport_animaux",
        "famille"
      ))
    }

    "throw an exception on invalid reasons" in {
      Authorization.unifyValidReasons(Seq("invalid")) must throwA[
        RuntimeException
      ]
    }
  }

  "orderedCanonicalValidReasons" should {
    "work as expected" in {
      Authorization.orderedCanonicalValidReasons(
        Seq("santé", "promenade", "famille", "travail")
      ) must be equalTo (Seq(
        "travail",
        "sante",
        "famille",
        "sport_animaux"
      ))
      Authorization.orderedCanonicalValidReasons(
        Seq("famille", "santé", "promenade", "travail")
      ) must be equalTo (Seq(
        "travail",
        "sante",
        "famille",
        "sport_animaux"
      ))
    }
  }

}
