import java.time._
import java.time.format.DateTimeFormatter

import Bot._
import PDFBuilder.BuildPDF
import TelegramSender.{DeletePreviousMessage, SendFile, SendText}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.methods.ParseMode
import com.bot4s.telegram.methods.ParseMode.ParseMode
import com.bot4s.telegram.models._
import models.DBProtocol.DBCommand
import models.{Authorization, DBProtocol, PersonalData}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

// This actor is in charge of handling a conversation with a single user. It will try to load
// information about the user from the database, and if it can't (information missing or database
// error) the user will be prompted for it then the data will be saved into the database.
//
// Once it has the data, the bot will accept commands to generate certificates. It will use local
// information (obtained from the database or acquired as described in the first step) so it will
// not need to use the database again, unless the user requests to delete their private information
// and start again at the first step.
private class ChatterBot(
    context: ActorContext[ChatterBot.ChatterBotControl],
    client: RequestHandler[Future],
    user: User,
    pdfBuilder: ActorRef[PDFBuilder.BuildPDF],
    db: ActorRef[DBCommand],
    debugActor: Option[ActorRef[String]]
) {

  import ChatterBot._

  private[this] implicit val ec: ExecutionContextExecutor =
    context.executionContext
  private[this] implicit val timeout: Timeout = 5.seconds

  // Actor in charge of sending ordered outgoing messages to the peer we are in conversation
  // with. We establish a death pact with this actor so that we die if it crashes.
  private[this] val outgoing = context.spawn(TelegramSender(client), "sender")
  context.watch(outgoing)

  /**
    * Send a message to the user.
    *
    * @param text the message to send
    * @param keyboard the content of the keyboard to display, grouped by rows
    */
  private[this] def sendText(
      text: String,
      keyboard: Seq[Seq[String]] = Seq(),
      parseMode: Option[ParseMode] = None
  ): Unit =
    outgoing ! SendText(ChatId(user.id), text, keyboard, parseMode)

  /**
    * Send a file to the user. The user will be presented with the "bot is uploading a file" notification
    * in Telegram.
    *
    * @param file the file to send
    * @param caption if defined, the caption to display to the user
    */
  private[this] def sendFile(
      file: InputFile,
      caption: Option[String] = None
  ): Unit =
    outgoing ! SendFile(ChatId(user.id), file, caption)

  private[this] def deleteMessage(messageId: Int): Unit =
    outgoing ! DeletePreviousMessage(ChatId(user.id), messageId)

  // Successive data to ask the user. The content of each tuple are:
  //  - the prompt
  //  - a checker function which can return either None if the entry is ok
  //    or Some(errorMessage) to display to the user
  //  - a suggestions function taking what's been entered so far in order
  //    to propose possible alternatives as a keyboard to the user
  private[this] val fields: Seq[
    (
        String,
        String => Option[String],
        Seq[String] => Seq[String]
    )
  ] = Seq(
    ("Entrez votre prénom", _ => None, _ => Seq(user.firstName)),
    ("Entrez votre nom", _ => None, _ => user.lastName.toSeq),
    (
      "Entrez votre date de naissance (jj/mm/aaaa)",
      checkBirthDate,
      _ => Seq()
    ),
    ("Entrez votre ville de naissance", _ => None, _ => Seq()),
    (
      "Entrez votre adresse de confinement sans le code postal ni la ville",
      _ => None,
      _ => Seq()
    ),
    ("Entrez votre code postal", checkZipCode, _ => Seq()),
    (
      "Entrez votre ville",
      _ => None,
      data => citiesFromZipCode(data.last)
    )
  )

  // Start by requesting stored data about the user. We treat an error
  // from the database as the absence of the document; the consequence is
  // that the user will have to re-enter the information again and no important
  // loss of functionality will occur.
  context
    .ask[DBCommand, Option[PersonalData]](db, DBProtocol.Load(user.id, _)) {
      case Success(Some(data)) => CachedData(data)
      case _                   => NoCachedData
    }

  /**
    * The start of the bot logic is here. We will first wait for the data to
    * arrive from the database and then switch to the `handleCommands`
    * behavior. If we could not get data about this user, because of nothing is
    * stored in the database or if there was a database error, we only handle
    * common commands such as `/help`, `/privacy` or `/data` until the user enters
    * `/start` to start data collection in which case we branch to `requestData`.
    */
  private val startingPoint: Behavior[ChatterBotControl] = {
    Behaviors.withStash[ChatterBotControl](5) { buffer =>
      Behaviors.receiveMessage {
        case CachedData(data) =>
          buffer.unstashAll(handleCommands(data))
        case NoCachedData =>
          buffer.unstashAll(Behaviors.receiveMessage {
            case FromUser(PrivateCommand("start", _)) =>
              handleStart()
            case FromUser(PrivateCommand("privacy", _)) =>
              privacyPolicy()
              Behaviors.same
            case FromUser(PrivateCommand("data", _)) =>
              sendText(
                s"Je ne connais que votre numéro unique Telegram à ce stade : ${user.id}"
              )
              Behaviors.same
            case FromUser(PrivateCommand("help", _)) =>
              help()
              Behaviors.same
            case FromUser(PrivateCallbackQuery(Some(data))) =>
              deleteMessage(data.toInt)
              Behaviors.same
            case _: FromUser =>
              sendText("Commencez par /start")
              Behaviors.same
            case other =>
              context.log.warn(s"Received spurious $other")
              Behaviors.same
          })
        case other =>
          buffer.stash(other)
          Behaviors.same
      }
    }
  }

  /**
    * Common handler for the `/start` function. When we arrive here, either we previously didn't find data
    * about the user in the database or the data was explicitely deleted using the `/start` command.
    *
    * @return the `requestData` behavior with empty data
    */
  private[this] def handleStart(): Behavior[ChatterBotControl] = {
    sendText(
      "Collecte des données personnelles - vous pourrez les effacer en refaisant /start et contrôler ce qui est connu en utilisant /data"
    )
    requestData(Seq())
  }

  /**
    * Collect data about the user.
    *
    * @param textualData the data we have so far in the same order as the [[models.PersonalData PersonalData]] case class
    * @return the behavior to handle user input
    */
  private[this] def requestData(
      textualData: Seq[String]
  ): Behavior[ChatterBotControl] = {
    val (fieldText, checker, fieldProposal) = fields(
      textualData.length
    )
    sendText(
      fieldText,
      Seq(fieldProposal(textualData)) // All proposals on one raw
    )
    Behaviors.receiveMessage {
      case CachedData(_) | NoCachedData =>
        context.log.warn(
          s"Database result for user ${user.id} arrived too late"
        )
        Behaviors.same
      case FromUser(PrivateCommand("start", _)) =>
        handleStart()
      case FromUser(PrivateCommand("privacy", _)) =>
        privacyPolicy()
        requestData(textualData)
      case FromUser(PrivateCommand("data", _)) =>
        if (textualData.isEmpty) {
          sendText(
            s"Je ne connais que votre numéro unique Telegram à ce stade : ${user.id}"
          )
        } else {
          sendText(
            ((s"À ce stade, je connais votre numéro unique Telegram (${user.id}), et vous avez rentré " +
              "les informations partielles suivantes (non encore stockées dans la base de données) :") +: textualData)
              .mkString("\n- ")
          )
        }
        requestData(textualData)
      case FromUser(PrivateCommand("help", _)) =>
        help()
        requestData(textualData)
      case FromUser(PrivateCommand(_, _)) =>
        sendText(
          "Impossible de lancer une commande tant que les informations ne sont pas disponibles"
        )
        requestData(textualData)
      case FromUser(PrivateMessage(text)) =>
        checker(text) match {
          case Some(errorMsg) =>
            sendText(errorMsg)
            requestData(textualData)
          case None =>
            if (textualData.length + 1 == fields.length) {
              val data = PersonalData(
                textualData(1),
                textualData.head,
                parseDate(textualData(2)),
                textualData(3),
                textualData(4),
                textualData(5),
                text
              )
              sendText(
                "Vous avez saisi les informations suivantes :\n" + formatData(
                  user,
                  data
                ) + "\nUtilisez /start en cas d'erreur."
              )
              db ! DBProtocol.Save(user.id, data)
              debugActor.foreach(
                _ ! s"Inserting data for ${data.firstName} ${data.lastName}"
              )
              offerCommands(data)
            } else {
              requestData(textualData :+ text)
            }
        }
      case FromUser(PrivateCallbackQuery(Some(data))) =>
        deleteMessage(data.toInt)
        Behaviors.same
      case msg =>
        context.log.warn(s"Unexpected message: $msg")
        Behaviors.same
    }
  }

  /**
    * Send common shortcuts to the user as a dedicated keyboard and prompt for a command.
    *
    * @param data the user data
    * @return the behavior to handle incoming commands
    */
  private[this] def offerCommands(
      data: PersonalData
  ): Behavior[ChatterBotControl] = {
    sendButtonsText()
    handleCommands(data)
  }

  /**
    * Handle incoming commands.
    *
    * @param data the user data
    * @return the behavior to handle incoming commands
    */
  private[this] def handleCommands(
      data: PersonalData
  ): Behavior[ChatterBotControl] = {
    Behaviors.receiveMessage {
      case CachedData(_) | NoCachedData =>
        context.log.warn(
          s"Database result for user ${user.id} arrived too late"
        )
        Behaviors.same
      case FromUser(PrivateMessage(_)) =>
        sendText(
          "J'ai déjà toutes les informations utiles, essayez /help pour voir les commandes disponibles"
        )
        offerCommands(data)
      case FromUser(PrivateCommand("start", _)) =>
        db ! DBProtocol.Delete(user.id)
        sendText(
          "Toutes vos données personnelles ont été définitivement supprimées de la base de donnée"
        )
        handleStart()
      case FromUser(PrivateCommand("privacy", _)) =>
        privacyPolicy()
        Behaviors.same
      case FromUser(PrivateCommand("help", _)) =>
        help()
        Behaviors.same
      case FromUser(PrivateCommand("data", _)) =>
        sendText(
          s"Je dispose des données personnelles suivantes :\n" + formatData(
            user,
            data
          )
        )
        Behaviors.same
      case FromUser(PrivateCommand("autre", Seq())) =>
        sendText("Il manque le(s) motif(s) de sortie")
        Behaviors.same
      case FromUser(PrivateCommand("autre", args)) =>
        handlePDFRequest(data, args.head.split("""[;,+-]""").toSeq, args.tail)
        Behaviors.same
      case FromUser(PrivateCommand("vierge", _)) =>
        handleEmptyPDFRequest(data)
        Behaviors.same
      case FromUser(PrivateCommand(command, _)) =>
        sendText(s"Commande /$command inconnue")
        Behaviors.same
      case PDFSuccess(content, caption) =>
        sendFile(InputFile("attestation.pdf", content), caption)
        context.log.info(
          s"""Document "${caption
            .getOrElse("<no title>")}" generated for ${data.firstName} ${data.lastName}"""
        )
        debugActor.foreach(_ ! s"""Sending document "${caption.getOrElse(
          "<no title>"
        )}" to ${data.firstName} ${data.lastName}""")
        offerCommands(data)
      case PDFFailure(e) =>
        sendText(
          s"Erreur interne lors de la génération du PDF, reessayez plus tard: $e"
        )
        offerCommands(data)
      case FromUser(PrivateCallbackQuery(Some(data))) =>
        deleteMessage(data.toInt)
        Behaviors.same
    }
  }

  private[this] def privacyPolicy(): Unit =
    sendText(
      GlobalConfig.privacyPolicy.getOrElse("No privacy policy available"),
      parseMode = Some(ParseMode.Markdown)
    )

  private[this] def help(): Unit =
    sendText(
      GlobalConfig.help.getOrElse("No help available"),
      parseMode = Some(ParseMode.Markdown)
    )

  private[this] def sendButtonsText(): Unit =
    sendText("Choisissez un certificat à générer", defaultButtons)

  /**
    * Send an empty certificate to print and fill by hand.
    *
    * @param data the user data
    */
  private[this] def handleEmptyPDFRequest(data: PersonalData): Unit = {
    val caption = "Attestation pré-remplie à imprimer"
    implicit val timeout: Timeout = 10.seconds
    context.ask[BuildPDF, Array[Byte]](
      pdfBuilder,
      BuildPDF(
        data,
        None,
        _
      )
    ) {
      case Success(document) => PDFSuccess(document, Some(caption))
      case Failure(e)        => PDFFailure(e)
    }
  }

  /**
    * Handle a PDF request incoming command.
    *
    * @param data the user data
    * @param reasons the reasons for going out
    * @param args the arguments given by the user (such as "oubli" or an explicit hour)
    */
  private[this] def handlePDFRequest(
      data: PersonalData,
      reasons: Seq[String],
      args: Seq[String]
  ): Unit = {
    val (validReasons, invalidReasons) = {
      val (v, i) = reasons.partition(Authorization.reasons.contains)
      (v.toSet.toSeq, i.toSet.toSeq)
    }
    if (invalidReasons.nonEmpty) {
      val next = if (validReasons.isEmpty) {
        "Aucun motif valable trouvé, génération du PDF impossible"
      } else { "Seuls les autres motifs seront utilisés" }
      sendText(
        s"Les motifs suivants sont invalides : ${invalidReasons.mkString(", ")}. $next."
      )
    }
    if (validReasons.isEmpty) {
      return
    }
    if (validReasons.length > 1) {
      sendText(
        "⚠️ Attention, rien dans les textes réglementaires ne semble indiquer qu'il est possible " +
          "d'utiliser plusieurs motifs simultanément"
      )
    }
    parseHour(args) match {
      case Right(outputDateTime) =>
        val madeTime = if (outputDateTime.getMinute % 5 == 0) {
          outputDateTime.minusMinutes(scala.util.Random.nextInt(2) + 1)
        } else {
          outputDateTime
        }
        val day = outputDateTime.getDayOfWeek.toFrenchDay
        val caption =
          s"Sortie ${validReasons.mkString("+")} $day à ${utils.timeText(outputDateTime)}"
        implicit val timeout: Timeout = 10.seconds
        context.ask[BuildPDF, Array[Byte]](
          pdfBuilder,
          BuildPDF(
            data,
            Some(
              Authorization(
                output = outputDateTime,
                made = madeTime,
                reasons = validReasons
              )
            ),
            _
          )
        ) {
          case Success(document) => PDFSuccess(document, Some(caption))
          case Failure(e)        => PDFFailure(e)
        }
      case Left(text) =>
        sendText(text)
        sendButtonsText()
    }

  }

}

object ChatterBot {

  /**
    * Build a chatter bot for an individual conversation.
    *
    * @param client the Telegram client to send outgoing messages
    * @param user the Telegram bot API `User` structure describing the peer
    * @param pdfBuilder the actor able to build PDF documents
    * @param db the database to retrieve user data from or store it into
    * @param debugActor if defined, the actor to send debugging messages to
    * @return
    */
  def apply(
      client: RequestHandler[Future],
      user: User,
      pdfBuilder: ActorRef[PDFBuilder.BuildPDF],
      db: ActorRef[DBCommand],
      debugActor: Option[ActorRef[String]]
  ): Behavior[PerChatBotCommand] =
    Behaviors
      .setup[ChatterBotControl](
        new ChatterBot(_, client, user, pdfBuilder, db, debugActor).startingPoint
      )
      .transformMessages {
        // We expand commands "/sport" and "/courses" into the generic command here
        case PrivateCommand(command @ ("sport" | "courses"), args) =>
          FromUser(PrivateCommand("autre", command +: args))
        case fromUser => FromUser(fromUser)
      }

  private sealed trait ChatterBotControl
  private case class FromUser(fromUser: PerChatBotCommand)
      extends ChatterBotControl

  private case class PDFSuccess(content: Array[Byte], caption: Option[String])
      extends ChatterBotControl
  private case class PDFFailure(e: Throwable) extends ChatterBotControl

  private case class CachedData(data: PersonalData) extends ChatterBotControl
  private case object NoCachedData extends ChatterBotControl

  private def parseDate(text: String): LocalDate =
    LocalDate.parse(text, DateTimeFormatter.ofPattern("d/M/y"))

  /**
    * Check that a birth date is at the right format.
    *
    * @param text the birth date
    * @return the error message to display if the date is invalid
    */
  private def checkBirthDate(text: String): Option[String] = {
    Try(parseDate(text)) match {
      case Success(_) => None
      case Failure(_) =>
        Some("Date non reconnue, veuillez la ressaisir au format attendu")
    }
  }

  /**
    * Check that the zip code is at the right format.
    *
    * @param text the zip code
    * @return the error message to display if the zip code is invalid
    */
  private def checkZipCode(text: String): Option[String] = {
    Try(Integer.parseInt(text)) match {
      case Success(_) => None
      case Failure(_) =>
        Some("Format de code postal non reconnu, veuillez le ressaisir")
    }
  }

  private val defaultButtons: Seq[Seq[String]] = {
    Seq(
      Seq("/sport", "/courses"),
      Seq(
        s"/sport oubli",
        s"/courses oubli"
      )
    )
  }

  /**
    * Parse command arguments into a date and time.
    *
    * @param args the arguments (nothing explicit, "oubli", "11h30")
    * @return a corresponding date and time in `Right` or an error message in `Left`
    */
  private def parseHour(args: Seq[String]): Either[String, LocalDateTime] = {
    val now = LocalDateTime.now()
    args match {
      case Seq("oubli") =>
        Right(
          now.minusMinutes(20 + now.getMinute - (now.getMinute / 5f).round * 5)
        )
      case Seq(spec) =>
        try {
          val time =
            LocalTime.parse(
              spec.replace(':', 'h'),
              DateTimeFormatter.ofPattern("H'h'mm")
            )
          addCredibleDate(time, now)
            .map(dt => Right(dt))
            .getOrElse(
              Left(
                "Cette heure est trop différente de l'heure courante, je ne sais pas s'il s'agit " +
                  "d'hier ou d'aujourd'hui. Je ne suis pas prévu pour ça."
              )
            )
        } catch {
          case _: Exception => Left("Heure non conforme")
        }
      case Seq() => Right(now)
      case _     => Left("Trop de paramètres")
    }
  }

  /**
    * Add a credible date to the outputTime if possible. It will be either yesterday, today, or tomorrow, whatever makes
    * the difference with nowDateTime smaller.
    *
    * @param outputTime the output time to augment with a date
    * @param nowDateTime the reference date and time
    * @return Either the output time with a credible date, or None if 8 hours or more (approximately) separate both
    *         inputs.
    */
  def addCredibleDate(
      outputTime: LocalTime,
      nowDateTime: LocalDateTime
  ): Option[LocalDateTime] = {
    val (nowDate, nowTime) = (nowDateTime.toLocalDate, nowDateTime.toLocalTime)
    val hoursBetweenNowAndOutput =
      (outputTime.getHour - nowTime.getHour + 24) % 24
    if (hoursBetweenNowAndOutput > 8 && hoursBetweenNowAndOutput < 16) {
      return None
    }
    val outputTimeToday = outputTime.atDate(nowDate)
    val outputIsLater = hoursBetweenNowAndOutput < 8
    val outputIsSmaller = outputTime.getHour < nowTime.getHour
    Some((outputIsLater, outputIsSmaller) match {
      case (true, false) | (false, true) => outputTimeToday
      case (false, false)                => outputTimeToday.minusDays(1)
      case (true, true)                  => outputTimeToday.plusDays(1)
    })
  }

  private def formatData(user: User, data: PersonalData): String =
    s"""- État-civil : ${data.firstName} ${data.lastName}
       |- Né(e) : le ${data.birthDateText} à ${data.birthPlace}
       |- Adresse de confinement : ${data.street} ${data.zip} ${data.city}
       |- Numéro unique Telegram : ${user.id}
       |""".stripMargin

  private def citiesFromZipCode(zipCode: String): Seq[String] =
    ZipCodes.fromZipCodes.get(zipCode).map(_.sorted).getOrElse(Seq())

  /**
    * Enrich Java's `DayOfWeek` enumeration with a new `toFrenchDay` field
    */
  private implicit class FrenchDayOfWeek(dow: DayOfWeek) {
    val toFrenchDay: String = dow match {
      case DayOfWeek.MONDAY    => "lundi"
      case DayOfWeek.TUESDAY   => "mardi"
      case DayOfWeek.WEDNESDAY => "mercredi"
      case DayOfWeek.THURSDAY  => "jeudi"
      case DayOfWeek.FRIDAY    => "vendredi"
      case DayOfWeek.SATURDAY  => "samedi"
      case DayOfWeek.SUNDAY    => "dimanche"
    }
  }
}
