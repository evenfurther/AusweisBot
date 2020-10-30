import java.time.{LocalDate, LocalDateTime, LocalTime}

import ChatterBot.{ChatterBotControl, FrenchDayOfWeek}
import TelegramSender.{SendFile, SendText, TelegramOutgoingControl}
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import com.bot4s.telegram.models.InputFile.Contents
import com.bot4s.telegram.models.{ChatId, User}
import models.DBProtocol.{DBCommand, Delete, Load, Save}
import models.{Authorization, PersonalData}
import org.specs2.mutable._

class ChatterBotSpec extends Specification {

  trait WithTestKit extends After {
    val testKit = ActorTestKit()
    val outgoing = testKit.createTestProbe[TelegramOutgoingControl]()
    val pdfBuilder = testKit.createTestProbe[PDFBuilder.BuildPDF]()
    val db = testKit.createTestProbe[DBCommand]()
    val debug = testKit.createTestProbe[String]()
    val user = User(42, false, "John")
    val chatterBot = testKit.spawn(Behaviors.setup[ChatterBotControl] {
      context =>
        new ChatterBot(
          context,
          outgoing.ref,
          user,
          pdfBuilder.ref,
          db.ref,
          Some(debug.ref)
        ).startingPoint
    })

    override def after: Any = testKit.shutdownTestKit()

    def sendMessage(msg: String): Unit =
      chatterBot ! ChatterBot.FromMainBot(Bot.PrivateMessage(msg))

    def sendMessages(msgs: String*): Unit =
      msgs.foreach(sendMessage)

    def sendCommand(command: String, args: Seq[String] = Seq()): Unit =
      chatterBot ! ChatterBot.FromMainBot(Bot.PrivateCommand(command, args))

    val modelData: PersonalData = PersonalData(
      "Doe",
      "John",
      LocalDate.of(1983, 4, 1),
      "Lyon",
      "12 rue de la liberté",
      "91842",
      "Trifouillis-les-Oies"
    )

    def withNoDatabaseEntry(): Unit = {
      db.expectMessageType[Load] match {
        case Load(42, replyTo) => replyTo ! None
        case _                 => failure
      }
    }

    def withDatabaseEntry(): Unit = {
      db.expectMessageType[Load] match {
        case Load(42, replyTo) => replyTo ! Some(modelData)
        case _                 => failure
      }
    }

    val now = LocalDateTime.now()
    val hour = now.getHour
    val day = now.getDayOfWeek

  }

  "The ChatterBot" should {
    "require that /start be sent if no entry for user is found in database" in new WithTestKit {
      withNoDatabaseEntry()
      sendMessage("foo")
      outgoing.expectMessageType[SendText].text must contain("/start")
      sendMessage("foo")
      outgoing.expectMessageType[SendText].text must contain("/start")
      sendCommand("start")
      outgoing.expectMessageType[SendText].text must contain(
        "Collecte des données personnelles"
      )
      outgoing.expectMessageType[SendText].text must contain(
        "prénom"
      )
    }

    "insert data into the database when data collection is done" in new WithTestKit {
      withNoDatabaseEntry()
      sendCommand("start")
      sendMessages(
        modelData.firstName,
        modelData.lastName,
        "1/4/1983",
        modelData.birthPlace,
        modelData.street,
        modelData.zip,
        modelData.city
      )
      db.expectMessage(Save(42, modelData))
    }

    "delete existing data as soon as /start is issued" in new WithTestKit {
      withDatabaseEntry()
      sendCommand("start")
      db.expectMessage(Delete(42))
    }

    "request and send a PDF on demand" in new WithTestKit {
      withDatabaseEntry()
      sendCommand("autre", Seq("santé+promenade", s"${hour}h30"))
      pdfBuilder.expectMessageType[PDFBuilder.BuildPDF] match {
        case PDFBuilder.BuildPDF(
            `modelData`,
            Some(Authorization(output, made, Seq("sante", "sport_animaux"))),
            replyTo
            ) =>
          output.toLocalTime must be equalTo (LocalTime.of(hour, 30))
          val mlt = made.toLocalTime
          mlt.getHour must be equalTo (hour)
          mlt.getMinute must be lessThan (30)
          mlt.getMinute must be greaterThan (25)
          replyTo ! Array(1, 2, 3)
        case s =>
          failure
      }
      outgoing.expectMessageType[SendText].text must contain(
        "plusieurs motifs simultanément"
      )
      val documentTitle = {
        f"Sortie santé/sport ou animaux ${day.toFrenchDay}%s à ${hour}%02d:30 pour John Doe"
      }
      outgoing.expectMessageType[SendFile] match {
        case SendFile(
            chatId: ChatId,
            Contents("attestation.pdf", Array(1, 2, 3)),
            Some(`documentTitle`)
            ) =>
          chatId must be equalTo (ChatId(42))
        case s =>
          failure
      }
      debug.expectMessage(s"""Sent document "$documentTitle"""")
    }
  }

  "parseBirthDate" should {
    "accept complete dates regardless of the year" in {
      ChatterBot.parseBirthDate("17/4/1870") must be equalTo (LocalDate.of(
        1870,
        4,
        17
      ))
      ChatterBot.parseBirthDate("17/4/2030") must be equalTo (LocalDate.of(
        2030,
        4,
        17
      ))
    }

    "complete two-digits years" in {
      ChatterBot.parseBirthDate("1/2/3") must be equalTo (LocalDate
        .of(2003, 2, 1))
      ChatterBot.parseBirthDate("17/1/78") must be equalTo (LocalDate
        .of(1978, 1, 17))
    }

    "fail on invalid dates" in {
      ChatterBot.parseBirthDate("1/17/78") must throwA[RuntimeException]
      ChatterBot.parseBirthDate("29/2/1900") must throwA[RuntimeException]
    }
  }

  "checkBirthDate" should {
    "reject invalid dates" in {
      ChatterBot.checkBirthDate("9/13/1990") must beSome
      ChatterBot.checkBirthDate("29/2/1900") must beSome
    }

    "accept plausible dates" in {
      ChatterBot.checkBirthDate("29/2/2000") must beNone
      ChatterBot.checkBirthDate("13/9/1922") must beNone
      ChatterBot.checkBirthDate("13/9/1967") must beNone
      ChatterBot.checkBirthDate("13/9/67") must beNone
      ChatterBot.checkBirthDate("22/3/2008") must beNone
      ChatterBot.checkBirthDate("22/3/8") must beNone
    }

    "reject dates in the future" in {
      ChatterBot.checkBirthDate("13/9/2030") must beSome
    }

    "reject dates too far in the past" in {
      ChatterBot.checkBirthDate("13/9/1890") must beSome
    }
  }

}
