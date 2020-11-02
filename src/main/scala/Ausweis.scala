import java.io.File
import java.nio.charset.Charset

import Bot.PerChatStarter
import BotUtils._
import akka.NotUsed
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{
  ActorRef,
  ActorSystem,
  Behavior,
  SupervisorStrategy,
  Terminated
}
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.models.{ChatId, User}
import com.typesafe.config.ConfigFactory
import models.Authorization
import org.apache.commons.io.IOUtils
import slogging.{LogLevel, LoggerConfig, PrintLoggerFactory}
import sun.misc.{Signal, SignalHandler}

import scala.concurrent.Future
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

object Ausweis extends App {

  val config = if (args.isEmpty) {
    ConfigFactory.load()
  } else {
    ConfigFactory
      .parseFile(new File(args(0)))
      .withFallback(ConfigFactory.load())
  }
  val ausweisConfig = config.getConfig("ausweis")

  LoggerConfig.factory = PrintLoggerFactory()
  LoggerConfig.level = LogLevel.INFO

  // Fill global configuration
  GlobalConfig.help = Some {
    val reasons: String = Authorization.reasonsAndAliases
      .zipWithIndex
      .map {
        case ((reason, aliases), i) =>
          s"- Case ${i+1} : `/$reason`${if (aliases.nonEmpty)
            s" (ou ${aliases.map(a => s"`/$a`").mkString(", ")})"
          else ""}"
      }
      .mkString("\n")
    IOUtils
      .resourceToString("/help.md", Charset.forName("UTF-8"))
      .replace("REASONS", reasons)
  }
  GlobalConfig.privacyPolicy = Some(
    IOUtils
      .resourceToString("/privacy.md", Charset.forName("UTF-8"))
      .replaceAll("CONTACT-EMAIL", ausweisConfig.getString("contact-email"))
  )

  // Main actor behavior, with no command
  val mainBehavior: Behavior[NotUsed] = Behaviors.setup { context =>
    // During development, the debug actor can receive information when
    // data enters the database or when a certificate is sent.
    val debugActor = if (ausweisConfig.hasPath("debug")) {
      val ref = context.spawn(
        Behaviors
          .supervise(
            DebugBot(
              ausweisConfig.getString("debug.telegram-token"),
              ChatId(ausweisConfig.getInt("debug.chat-id"))
            )
          )
          .onFailure[Throwable](SupervisorStrategy.restart),
        "debug"
      )
      Some(ref)
    } else {
      None
    }

    val botToken = ausweisConfig.getString("telegram-token")

    val botBehavior = if (ausweisConfig.getBoolean("disabled")) {
      DisabledBot(botToken, debugActor)
    } else {
      fullFeaturedBehavior(context, botToken, debugActor)
    }

    // Since the bot  might die if the connection with the Telegram servers is broken,
    // we restart it with an exponential backoff strategy in order not to hammer the servers
    // with repeated requests. In case of normal termination (for example after a global
    // shutdown request), terminate the system as well.
    val bot = context.spawn(
      Behaviors
        .supervise(botBehavior)
        .onFailure[Throwable](
          SupervisorStrategy
            .restartWithBackoff(5.seconds, 30.seconds, 0.2)
            .withResetBackoffAfter(5.minutes)
        ),
      "ausweis-bot"
    )
    context.watch(bot)

    // Catch SIGINT and SIGTERM, and start a proper shutdown procedure that
    // will warn users of an imminent shutdown if their unsaved personal data
    // will be lost.
    val signalHandler = new SignalHandler {
      override def handle(signal: Signal): Unit =
        bot ! Bot.InitiateGlobalShutdown
    }
    Signal.handle(new Signal("INT"), signalHandler)
    Signal.handle(new Signal("TERM"), signalHandler)

    Behaviors.receiveSignal {
      case (context, Terminated(_)) =>
        context.log.info("Main bot actor terminated, terminating ActorSystem")
        Behaviors.stopped
    }
  }

  ActorSystem(mainBehavior, "guardian")

  private def fullFeaturedBehavior(
      context: ActorContext[NotUsed],
      botToken: String,
      debugActor: Option[ActorRef[String]]
  ) = {
    // PDF builder actor
    val pdfBuilder = {
      val certificate = IOUtils.resourceToByteArray("/certificate.d1673940.pdf")
      context.spawn(
        Behaviors
          .supervise(PDFBuilder.makeActor(certificate))
          .onFailure[Throwable](SupervisorStrategy.restart),
        "PDF-builder"
      )
    }
    // CouchDB database actor
    val db = context.spawn(
      Behaviors
        .supervise(CouchDB(ausweisConfig.getString("database")))
        .onFailure[Throwable](SupervisorStrategy.restart),
      name = "CouchDB"
    )
    // Starter for new actors in charge of an individual conversation
    // with a Telegram user. Stop an actor after it has been idle for
    // 15 minutes. It may cause data loss if someone is entering their
    // information and makes a pause for 15 minutes right in the middle,
    // but this is supposed to be an interactive service.
    val perChatStarter: PerChatStarter = (
        user: User,
        client: RequestHandler[Future],
        parent: ActorRef[Bot.RequestChatShutdown],
        debugActor: Option[ActorRef[String]]
    ) =>
      ChatterBot(client, user, pdfBuilder, db, debugActor)
        .withIdleTimeout(
          15.minutes,
          parent,
          Bot.RequestChatShutdown(user.id, "d'inactivité de votre part")
        ) // Main bot.
        .withThrottling(
          FiniteDuration(
            ausweisConfig.getDuration("interrequest-delay").toNanos,
            TimeUnit.NANOSECONDS
          ),
          ausweisConfig.getInt("max-queued-requests")
        )
    Bot(botToken, perChatStarter, debugActor)
  }
}
