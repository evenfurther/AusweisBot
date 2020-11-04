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
import scala.util.Success

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
      "John",
      "Doe",
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
          replyTo ! Success(Array(1, 2, 3))
        case s =>
          failure
      }
      outgoing.expectMessageType[SendText].text must contain(
        "plusieurs motifs simultanément"
      )
      val documentTitle = {
        f"Sortie santé/promenade ${day.toFrenchDay}%s à ${hour}%02d:30 pour John Doe"
      }
      outgoing.expectMessageType[SendFile] match {
        case SendFile(
            chatId: ChatId,
            Contents("attestation.pdf", Array(1, 2, 3)),
            Some(`documentTitle`)
            ) =>
          chatId must be equalTo (ChatId(42))
        case s =>
          println(s)
          println(documentTitle)
          failure
      }
      debug.expectMessage(s"""Sent document "$documentTitle"""")
    }

    "be able to update identity" in new WithTestKit {
      withDatabaseEntry()
      sendCommand("i")
      db.expectMessage(Delete(42))
      val mod = modelData.copy(
        firstName = "Sylvie",
        lastName = "Martin",
        birthDate = LocalDate.of(2000, 2, 1),
        birthPlace = "Marseille"
      )
      sendMessages(mod.firstName, mod.lastName, "1/2/2000", mod.birthPlace)
      db.expectMessage(Save(42, mod))
    }

    "be able to update location" in new WithTestKit {
      withDatabaseEntry()
      sendCommand("l")
      db.expectMessage(Delete(42))
      val mod = modelData.copy(
        street = "10 rue du moulin",
        zip = "75013",
        city = "Paris"
      )
      sendMessages(mod.street, mod.zip, mod.city)
      db.expectMessage(Save(42, mod))
    }
  }

}
