import Bot.BotCommand
import BotUtils.IdempotentShutdown
import TelegramSender.SendText
import akka.actor.ActorSystem
import akka.actor.typed._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.clients.AkkaHttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.bot4s.telegram.methods.ParseMode
import com.bot4s.telegram.models._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

private class DisabledBot(
    context: ActorContext[BotCommand],
    token: String,
    debugActor: Option[ActorRef[String]]) extends AbstractBehavior[BotCommand](context)
  with TelegramBot
  with Polling
  with IdempotentShutdown
  with Commands[Future] {

  import Bot._

  private[this] implicit val system: ActorSystem = context.system.toClassic
  override val client: RequestHandler[Future] = new AkkaHttpClient(token)

  debugActor.foreach(_ ! "Starting")

  context.pipeToSelf(run())(ConnectionShutdown)

  val outgoing: ActorRef[TelegramSender.TelegramOutgoingData] =
    context.spawn(TelegramSender(client), "sender")
  context.watch(outgoing)

  override def receiveMessage(msg: Message): Future[Unit] = {
    context.self ! IncomingMessage(msg)
    Future.successful(())
  }

  override def onMessage(msg: BotCommand): Behavior[BotCommand] = msg match {
    case ConnectionShutdown(Success(_)) =>
      context.log.error("Telegram connection terminated spontaneously")
      throw new IllegalStateException(
        "Telegram connection terminated spontaneously")
    case ConnectionShutdown(Failure(t)) =>
      context.log.error("Telegram connection terminated on error", t)
      throw t
    case IncomingMessage(message) =>
      message.text.foreach { _ =>
        message.from.foreach { user =>
          outgoing ! SendText(
            ChatId(user.id),
            DisabledBot.disabledMessage,
            parseMode = Some(ParseMode.Markdown))
        }
      }
      Behaviors.same
    case InitiateGlobalShutdown =>
      shutdown()
      debugActor.foreach(_ ! "Shutting down")
      // Wait for some time before starting termination in order to let
      // the debug message get through. Reuse the same message.
      Behaviors.withTimers { timers =>
        timers.startSingleTimer(InitiateGlobalShutdown, 100.milliseconds)
        Behaviors
          .receiveMessage {
            case InitiateGlobalShutdown => Behaviors.stopped
            case _                      => Behaviors.same
          }
      }
    case msg =>
      context.log.error(s"Unexpected message: $msg")
      throw new IllegalStateException(s"unexpected message: $msg")
  }

  override def onSignal: PartialFunction[Signal, Behavior[BotCommand]] = {
    case PostStop | PreRestart =>
      shutdown()
      Behaviors.stopped
  }

}

object DisabledBot {

  /**
   * Make a bot connecting to Telegram servers and telling users that they
   * don't need an authorization anymore.
   *
   * @param token
   *   the Telegram bot token given by
   *   [[https://telegram.me/BotFather BotFather]]
   * @param debugActor
   *   if defined, the actor to send debugging information to
   * @return
   */
  def apply(
    token: String,
    debugActor: Option[ActorRef[String]]): Behavior[BotCommand] =
    Behaviors.setup(new DisabledBot(_, token, debugActor))

  private val disabledMessage =
    """
      | Les changements permanents de règles et de leur inteprétation auront fini par avoir raison
      | d'AusweisBot. Le temps et l'énergie nécessaires pour s'adapter aux nouvelles conditions
      | tout en restant simple d'utilisation excède les ressources raisonnablement mobilisables.
      | Ce service est suspendu et toutes les données personnelles connues à la date de la suspension
      | ont été effacées.
      |
      | Pour vos besoins d'attestation, vous pouvez utiliser le
      | [site gouvernemental](https://media.interieur.gouv.fr/attestation-deplacement-derogatoire-covid-19/)
      | ou l'application [TousAntiCovid](https://www.gouvernement.fr/info-coronavirus/tousanticovid). Cette
      | dernière permet de générer des attestations que la fonctionalité de tracing bluetooth soit activée
      | ou non.
      |
      | Merci d'avoir utilisé AusweisBot.
      |""".stripMargin
      .replace("\n\n ", "¶")
      .replace('\n', ' ')
      .replace("¶", "\n\n")

}
