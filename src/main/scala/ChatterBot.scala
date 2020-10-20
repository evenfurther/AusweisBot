import java.time._
import java.time.format.{DateTimeFormatter, ResolverStyle}

import Bot._
import PDFBuilder.BuildPDF
import TelegramSender.{SendFile, SendText, TelegramOutgoingData}
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
    outgoing: ActorRef[TelegramOutgoingData],
    user: User,
    pdfBuilder: ActorRef[PDFBuilder.BuildPDF],
    db: ActorRef[DBCommand],
    debugActor: Option[ActorRef[String]]
) {

  import ChatterBot._

  private[this] implicit val ec: ExecutionContextExecutor =
    context.executionContext
  private[this] implicit val timeout: Timeout = 5.seconds

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
    * Send a PDF file to the user. The user will be presented with the "bot is uploading a file" notification
    * in Telegram.
    *
    * @param content the file content to send
    * @param caption if defined, the caption to display to the user
    */
  private[this] def sendCertificate(
      content: Array[Byte],
      caption: Option[String] = None
  ): Unit = {
    val doc = caption.getOrElse("without a title")
    context.log.info(s"""Sent document "$doc"""")
    debugActor.foreach(_ ! s"""Sent document "$doc"""")
    outgoing ! SendFile(
      ChatId(user.id),
      InputFile("attestation.pdf", content),
      caption
    )
  }

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
  val startingPoint: Behavior[ChatterBotControl] = {
    Behaviors.withStash[ChatterBotControl](10) { buffer =>
      Behaviors.receiveMessage {
        case CachedData(data) =>
          buffer.unstashAll(handleCommands(data))
        case NoCachedData =>
          buffer.unstashAll(Behaviors.receiveMessage {
            case FromMainBot(PrivateCommand("start", _)) =>
              handleStart()
            case FromMainBot(PrivateCommand("privacy", _)) =>
              privacyPolicy()
              Behaviors.same
            case FromMainBot(PrivateCommand("data", _)) =>
              sendText(
                s"Je ne connais que votre numéro unique Telegram à ce stade : ${user.id}"
              )
              Behaviors.same
            case FromMainBot(PrivateCommand("help", _)) =>
              help()
              Behaviors.same
            case _: FromMainBot =>
              sendText("Commencez par /start")
              Behaviors.same
            case other =>
              context.log.warn(s"Received spurious $other")
              Behaviors.same
          })
        case FromMainBot(AnnounceShutdown(_)) =>
          Behaviors.stopped
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
    Behaviors.receiveMessage[ChatterBotControl] {
      case CachedData(_) | NoCachedData =>
        context.log
          .warn(s"Database result for user ${user.id} arrived too late")
        Behaviors.same
      case FromMainBot(PrivateCommand("start", _)) => handleStart()
      case FromMainBot(PrivateCommand("privacy", _)) =>
        privacyPolicy()
        requestData(textualData)
      case FromMainBot(PrivateCommand("data", _)) =>
        if (textualData.isEmpty) {
          sendText(
            s"Je ne connais que votre numéro unique Telegram à ce stade : ${user.id}"
          )
        } else {
          sendText(
            ((s"À ce stade, je connais votre numéro unique Telegram (${user.id}), et vous avez rentré " + "les informations partielles suivantes (non encore stockées dans la base de données) :") +: textualData)
              .mkString("\n- ")
          )
        }
        requestData(textualData)
      case FromMainBot(PrivateCommand("help", _)) =>
        help()
        requestData(textualData)
      case FromMainBot(PrivateCommand(_, _)) =>
        sendText(
          "Impossible de lancer une commande tant que les informations ne sont pas disponibles"
        )
        requestData(textualData)
      case FromMainBot(PrivateMessage(text)) =>
        checker(text) match {
          case Some(errorMsg) =>
            sendText(errorMsg)
            requestData(textualData)
          case None =>
            if (textualData.length + 1 == fields.length) {
              val data = PersonalData(
                textualData(1),
                textualData.head,
                parseBirthDate(textualData(2)),
                textualData(3),
                textualData(4),
                textualData(5),
                text
              )
              sendText(
                "Vous avez saisi les informations suivantes :\n" + formatData(
                  user,
                  data
                ) + "\nUtilisez /start en cas d'erreur. Vous allez maintenant pouvoir " + "utiliser les commandes pour générer des attestations. Envoyez " + "/help pour obtenir de l'aide sur les commandes disponibles."
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
      case FromMainBot(AnnounceShutdown(reason)) =>
        val msg = if (textualData.isEmpty) {
          s"Cette conversation est momentanément stoppée en raison $reason. Vous pouvez la reprendre " +
            s"à tout moment en utilisant la commande /start."
        } else {
          s"En raison $reason la collecte de données est interrompue et les données transmises jusqu'à " +
            "présent ne seront pas sauvegardées. Vous pouvez recommencer la collecte à tout moment en utilisant " +
            "la commande /start."
        }
        sendText(msg)
        // Wait for 100 milliseconds before stopping
        Behaviors
          .withTimers[Any] { timers =>
            val Stop = new Object
            timers.startSingleTimer(Stop, 100.milliseconds)
            Behaviors.receiveMessage {
              case Stop => Behaviors.stopped
              case msg =>
                context.log.warn(
                  s"Spurious message received during shutdown: $msg"
                )
                Behaviors.same
            }
          }
          .narrow
      case PDFSuccess(content, caption) =>
        sendCertificate(content, caption)
        Behaviors.same
      case PDFFailure(_) => Behaviors.same
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
      case FromMainBot(PrivateMessage(_)) =>
        sendText(
          "J'ai déjà toutes les informations utiles, essayez /help pour voir les commandes disponibles"
        )
        offerCommands(data)
      case FromMainBot(PrivateCommand("start", _)) =>
        db ! DBProtocol.Delete(user.id)
        sendText(
          "Toutes vos données personnelles ont été définitivement supprimées de la base de donnée"
        )
        handleStart()
      case FromMainBot(PrivateCommand("privacy", _)) =>
        privacyPolicy()
        Behaviors.same
      case FromMainBot(PrivateCommand("help", _)) =>
        help()
        Behaviors.same
      case FromMainBot(PrivateCommand("data", _)) =>
        sendText(
          s"Je dispose des données personnelles suivantes :\n" + formatData(
            user,
            data
          )
        )
        Behaviors.same
      case FromMainBot(PrivateCommand("autre", Seq())) =>
        sendText("Il manque le(s) motif(s) de sortie")
        Behaviors.same
      case FromMainBot(PrivateCommand("autre", args)) =>
        if (Authorization.valid(args.head)) {
          handlePDFRequest(data, args.head, args.tail)
        } else {
          sendText("Cette raison n'est pas connue")
        }
        Behaviors.same
      case FromMainBot(PrivateCommand("vierge", _)) =>
        handleEmptyPDFRequest(data)
        Behaviors.same
      case FromMainBot(PrivateCommand(command, _)) =>
        sendText(s"Commande /$command inconnue")
        Behaviors.same
      case FromMainBot(AnnounceShutdown(_)) =>
        Behaviors.stopped
      case PDFSuccess(content, caption) =>
        sendCertificate(content, caption)
        offerCommands(data)
      case PDFFailure(e) =>
        sendText(
          s"Erreur interne lors de la génération du PDF, réessayez plus tard: $e"
        )
        offerCommands(data)
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
    sendText(
      "Choisissez un certificat à générer (utilisez /help pour l'aide)",
      defaultButtons
    )

  /**
    * Send an empty certificate to print and fill by hand.
    *
    * @param data the user data
    */
  private[this] def handleEmptyPDFRequest(data: PersonalData): Unit = {
    val caption = s"Attestation pré-remplie à imprimer pour ${data.fullName}"
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
    * @param reason the reason for going out
    * @param args the arguments given by the user (such as "oubli" or an explicit hour)
    */
  private[this] def handlePDFRequest(
      data: PersonalData,
      reason: String,
      args: Seq[String]
  ): Unit = {
    parseHour(args) match {
      case Right(outputDateTime) =>
        val madeTime = if (outputDateTime.getMinute % 5 == 0) {
          outputDateTime.minusMinutes(scala.util.Random.nextInt(2) + 1)
        } else {
          outputDateTime
        }
        val day = outputDateTime.getDayOfWeek.toFrenchDay
        val caption =
          s"Sortie $reason $day à ${utils.timeText(outputDateTime)} pour ${data.fullName}"
        implicit val timeout: Timeout = 10.seconds
        context.ask[BuildPDF, Array[Byte]](
          pdfBuilder,
          BuildPDF(
            data,
            Some(
              Authorization(
                output = outputDateTime,
                made = madeTime,
                reason = reason
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
      .setup[ChatterBotControl] { context =>
        // Actor in charge of sending ordered outgoing messages to the peer we are in conversation
        // with. We establish a death pact with this actor so that we die if it crashes because
        // the whole logic is off if the user misses a message.
        val outgoing = context.spawn(TelegramSender(client), "sender")
        context.watch(outgoing)
        new ChatterBot(context, outgoing, user, pdfBuilder, db, debugActor).startingPoint
      }
      .transformMessages {
        // We expand commands "/animaux" and "/famille" into the generic command here
        case PrivateCommand(command @ ("animaux" | "famille"), args) =>
          FromMainBot(PrivateCommand("autre", command +: args))
        case fromMainBot => FromMainBot(fromMainBot)
      }

  sealed trait ChatterBotControl
  case class FromMainBot(fromMainBot: PerChatBotCommand)
      extends ChatterBotControl

  private case class PDFSuccess(content: Array[Byte], caption: Option[String])
      extends ChatterBotControl
  private case class PDFFailure(e: Throwable) extends ChatterBotControl

  private case class CachedData(data: PersonalData) extends ChatterBotControl
  private case object NoCachedData extends ChatterBotControl

  /**
    * Parse a textual date and return a plausible one. Any year in [0, 20] will
    * be added 2000 to it, and any year in [21, 99] will be added 1900 to it.
    * Of course it is best to supply the full year.
    * @param text the date to parse
    * @return a plausible date
    */
  def parseBirthDate(text: String): LocalDate = {
    val date = LocalDate.parse(
      text,
      DateTimeFormatter
        .ofPattern("d/M/u")
        .withResolverStyle(ResolverStyle.STRICT)
    )
    if (date.getYear <= 20) {
      date.plusYears(2000)
    } else if (date.getYear <= 100) {
      date.plusYears(1900)
    } else {
      date
    }
  }

  /**
    * Check that a birth date is at the right format.
    *
    * @param text the birth date
    * @return the error message to display if the date is invalid
    */
  def checkBirthDate(text: String): Option[String] = {
    Try(parseBirthDate(text)) match {
      case Success(date) =>
        if (date.isAfter(LocalDate.now())) {
          Some(
            "Je doute que vous soyez né(e) dans le futur, merci de saisir une date plausible"
          )
        } else if (date.isBefore(LocalDate.now().minusYears(110))) {
          Some(
            "À plus de 110 ans il n'est pas prudent de sortir, merci de saisir une date plausible"
          )
        } else {
          None
        }
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
      Seq("/animaux", "/famille"),
      Seq(
        s"/animaux oubli",
        s"/famille oubli"
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
  implicit class FrenchDayOfWeek(dow: DayOfWeek) {
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
