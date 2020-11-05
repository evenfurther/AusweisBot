import java.time._
import java.time.format.DateTimeFormatter

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
import models.{Authorization, DBProtocol, IncompletePersonalData, PersonalData}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}
import java.util.concurrent.TimeoutException

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

  private[this] def initialData =
    new IncompletePersonalData(Some(user.firstName), user.lastName)

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
    requestData(initialData)
  }

  /**
    * Collect data about the user.
    *
    * @param textualData the data we have so far in the same order as the [[models.PersonalData PersonalData]] case class
    * @return the behavior to handle user input
    */
  private[this] def requestData(
      partialData: IncompletePersonalData
  ): Behavior[ChatterBotControl] = {
    val (fieldText, fieldSuggestions, fieldSetter) =
      partialData.firstMissingField.get
    sendText(
      fieldText,
      Seq(fieldSuggestions) // All proposals on one raw
    )
    Behaviors.receiveMessage[ChatterBotControl] {
      case CachedData(_) | NoCachedData =>
        context.log
          .warn(s"Database result for user ${user.id} arrived too late")
        Behaviors.same
      case FromMainBot(PrivateCommand("start", _)) => handleStart()
      case FromMainBot(PrivateCommand("privacy", _)) =>
        privacyPolicy()
        requestData(partialData)
      case FromMainBot(PrivateCommand("data", _)) =>
        sendText(
          s"À ce stade, je connais les informations suivantes (non stockées) :\n$partialData\n- Numéro unique Telegram : ${user.id}"
        )
        requestData(partialData)
      case FromMainBot(PrivateCommand("help", _)) =>
        help()
        requestData(partialData)
      case FromMainBot(PrivateCommand("i", Seq())) =>
        partialData.stripIdentity(user.firstName, user.lastName)
        requestData(partialData)
      case FromMainBot(PrivateCommand("l", Seq())) =>
        partialData.stripAddress()
        requestData(partialData)
      case FromMainBot(PrivateCommand(_, _)) =>
        sendText(
          "Impossible de lancer une commande tant que les informations ne sont pas disponibles"
        )
        requestData(partialData)
      case FromMainBot(PrivateMessage(text)) =>
        fieldSetter(text) match {
          case Some(errorMsg) =>
            sendText(errorMsg)
            requestData(partialData)
          case None =>
            if (partialData.isComplete) {
              val data = partialData.toPersonalData
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
              requestData(partialData)
            }
        }
      case FromMainBot(AnnounceShutdown(reason)) =>
        val msg =
          s"En raison $reason la collecte de données est interrompue et les données transmises jusqu'à " +
            "présent ne seront pas sauvegardées. Vous pouvez recommencer la collecte à tout moment en utilisant " +
            "la commande /start."
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
      case PDFSuccess(content, caption, _) =>
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
      data: PersonalData,
      reasons: Seq[String] = Seq()
  ): Behavior[ChatterBotControl] = {
    sendButtonsText(reasons)
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
      case FromMainBot(PrivateCommand("i", Seq())) =>
        db ! DBProtocol.Delete(user.id)
        requestData(
          IncompletePersonalData
            .forgetIdentity(data, user.firstName, user.lastName)
        )
      case FromMainBot(PrivateCommand("l", Seq())) =>
        db ! DBProtocol.Delete(user.id)
        requestData(IncompletePersonalData.forgetAddress(data))
      case FromMainBot(PrivateCommand("autre", Seq())) =>
        sendText("Il manque le(s) motif(s) de sortie")
        Behaviors.same
      case FromMainBot(PrivateCommand("autre", args)) =>
        handlePDFRequest(data, args.head.split("""[;,+-]""").toSeq, args.tail)
        Behaviors.same
      case FromMainBot(PrivateCommand("vierge", _)) =>
        handleEmptyPDFRequest(data)
        Behaviors.same
      case FromMainBot(PrivateCommand(command, _)) =>
        sendText(s"Commande /$command inconnue")
        Behaviors.same
      case FromMainBot(AnnounceShutdown(_)) =>
        Behaviors.stopped
      case PDFSuccess(content, caption, reasons) =>
        sendCertificate(content, caption)
        offerCommands(data, reasons)
      case PDFFailure(_: TimeoutException) =>
        sendText(
          "Le système est trop chargé en ce moment, réessayez un peu plus tard"
        )
        debugActor.foreach(
          _ ! s"Timeout for receiving PDF document for ${data.firstName} ${data.lastName}"
        )
        offerCommands(data)
      case PDFFailure(e) =>
        sendText(
          "Erreur interne lors de la génération du PDF, réessayez plus tard"
        )
        debugActor.foreach(
          _ ! s"Failure in PDF generation for ${data.firstName} ${data.lastName}: $e"
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

  private[this] def sendButtonsText(reasons: Seq[String] = Seq()): Unit =
    sendText(
      "Choisissez un certificat à générer (utilisez /help pour l'aide)",
      makeButtons(reasons)
    )

  /**
    * Send an empty certificate to print and fill by hand.
    *
    * @param data the user data
    */
  private[this] def handleEmptyPDFRequest(data: PersonalData): Unit = {
    val caption = s"Attestation pré-remplie à imprimer pour ${data.fullName}"
    implicit val timeout: Timeout = 10.seconds
    context.ask[BuildPDF, Try[Array[Byte]]](
      pdfBuilder,
      BuildPDF(
        data,
        None,
        _
      )
    )(_.flatten match {
      case Success(document) => PDFSuccess(document, Some(caption), Seq())
      case Failure(e)        => PDFFailure(e)
    })
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
    val (validReasons, invalidReasons, validReasonsRaw) = {
      val (v, i) = reasons.partition(Authorization.reasons.contains)
      (Authorization.unifyValidReasons(v), i.toSet.toSeq, v)
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
          s"Sortie ${validReasons.map(Authorization.prettyReason).mkString("/")} $day à ${utils
            .timeText(outputDateTime)} pour ${data.fullName}"
        implicit val timeout: Timeout = 10.seconds
        context.ask[BuildPDF, Try[Array[Byte]]](
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
        )(_.flatten match {
          case Success(document) =>
            PDFSuccess(document, Some(caption), validReasonsRaw)
          case Failure(e) => PDFFailure(e)
        })
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
        // We expand commands into the generic command here
        case PrivateCommand(
            command,
            args
            ) if Authorization.reasons.contains(command) =>
          FromMainBot(PrivateCommand("autre", command +: args))
        case fromMainBot => FromMainBot(fromMainBot)
      }

  sealed trait ChatterBotControl
  case class FromMainBot(fromMainBot: PerChatBotCommand)
      extends ChatterBotControl

  private case class PDFSuccess(
      content: Array[Byte],
      caption: Option[String],
      reasons: Seq[String]
  ) extends ChatterBotControl
  private case class PDFFailure(e: Throwable) extends ChatterBotControl

  private case class CachedData(data: PersonalData) extends ChatterBotControl
  private case object NoCachedData extends ChatterBotControl

  private def makeButtons(reasons: Seq[String]): Seq[Seq[String]] = {
    val latestReasons =
      Authorization
        .unifyReasons(reasons ++ Seq("sport", "courses", "travail"))
        .take(3)
    Seq(
      latestReasons.map("/" + _),
      latestReasons.map("/" + _ + " oubli")
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
              DateTimeFormatter.ofPattern("H'h'[mm]")
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
       |- Adresse de résidence : ${data.street} ${data.zip} ${data.city}
       |- Numéro unique Telegram : ${user.id}
       |""".stripMargin

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
