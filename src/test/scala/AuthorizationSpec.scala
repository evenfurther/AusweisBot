import models.Authorization
import org.specs2.mutable._

class AuthorizationSpec extends Specification {

  "canonical" should {
    "work as expected" in {
      Authorization.canonical("formation") must be equalTo (Some("travail"))
      Authorization.canonical("animal") must be equalTo (Some("animaux"))
      Authorization.canonical("famille") must be equalTo (Some("famille"))
      Authorization.canonical("sieste") must be equalTo (None)
    }
  }

  "valid" should {
    "work as expected" in {
      Authorization.valid("formation") must beTrue
      Authorization.valid("travail") must beTrue
      Authorization.valid("sieste") must beFalse
    }
  }

}
