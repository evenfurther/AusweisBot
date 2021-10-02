import org.specs2.mutable._

class BotSpec extends Specification {

  "PrivateCommand" should {
    "transform upper case values into lower case" in {
      val Bot.PrivateCommand(cmd, args) =
        Bot.PrivateCommand("Santé+Travail", Seq("OuBlI"))
      cmd must be equalTo ("santé+travail")
      args must be equalTo (Seq("oubli"))
    }
  }

}
