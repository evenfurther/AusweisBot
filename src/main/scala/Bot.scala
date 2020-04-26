import Bot.{BotCommand, PerChatStarter}
import BotUtils.IdempotentShutdown
import akka.actor.ActorSystem
import akka.actor.typed._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.api.{ChatActions, RequestHandler}
import com.bot4s.telegram.clients.AkkaHttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.bot4s.telegram.models._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

private class Bot(
    context: ActorContext[BotCommand],
    token: String,
    perChatStarter: PerChatStarter,
    debugActor: Option[ActorRef[String]]
) extends AbstractBehavior[BotCommand](context)
    with TelegramBot
    with Polling
    with IdempotentShutdown
    with Commands[Future]
    with ChatActions[Future] {

  import Bot._

  private[this] implicit val system: ActorSystem = context.system.toClassic
  override val client: RequestHandler[Future] = new AkkaHttpClient(token)

  private[this] val chatters: mutable.Map[Int, ActorRef[PerChatBotCommand]] =
    mutable.Map()

  debugActor.foreach(_ ! "Starting")

  context.pipeToSelf(run())(ConnectionShutdown)

  override def receiveMessage(msg: Message): Future[Unit] = {
    context.self ! IncomingMessage(msg)
    Future.successful(())
  }

  override def onMessage(msg: BotCommand): Behavior[BotCommand] = msg match {
    case ConnectionShutdown(Success(_)) =>
      context.log.error("Telegram connection terminated spontaneously")
      throw new IllegalStateException(
        "Telegram connection terminated spontaneously"
      )
    case ConnectionShutdown(Failure(t)) =>
      context.log.error("Telegram connection terminated on error", t)
      throw t
    case IncomingMessage(message) =>
      message.text.foreach { text =>
        message.from.foreach { user =>
          findOrSpawnChatterBot(user) ! parseText(text)
        }
      }
      Behaviors.same
    case RequestChatShutdown(id, reason) =>
      chatters.remove(id).foreach { ref =>
        context.unwatch(ref)
        ref ! AnnounceShutdown(reason)
      }
      Behaviors.same
    case InitiateGlobalShutdown =>
      shutdown()
      chatters.values.foreach(
        _ ! AnnounceShutdown("d'une mise à jour du bot ou du serveur")
      )
      debugActor.foreach(
        _ ! s"Shutting down (active sessions: ${chatters.size})"
      )
      // Wait for some time before starting termination in order to let
      // the debug message get through. Reuse the same message.
      Behaviors.withTimers { timers =>
        timers.startSingleTimer(InitiateGlobalShutdown, 100.milliseconds)
        Behaviors
          .receiveMessage {
            case InitiateGlobalShutdown => waitForChattersTermination()
            case _                      => Behaviors.same
          }
      }
  }

  // Wait until we have no more individual actors alive
  private def waitForChattersTermination(): Behavior[BotCommand] =
    if (chatters.isEmpty) {
      context.log.info("Global termination: ready to terminate")
      Behaviors.stopped
    } else {
      context.log.info(s"Global termination: ${chatters.size} actor(s) left")
      Behaviors.receiveMessage {
        case RequestChatShutdown(id, _) =>
          chatters.remove(id)
          waitForChattersTermination()
        case _ =>
          Behaviors.same
      }
    }

  private[this] def findOrSpawnChatterBot(
      user: User
  ): ActorRef[PerChatBotCommand] = {
    val ref = chatters.getOrElseUpdate(
      user.id, {
        val chatter = context.spawnAnonymous(
          perChatStarter(
            user,
            request,
            context.self.narrow[RequestChatShutdown],
            debugActor
          )
        )
        context.watchWith(chatter, RequestChatShutdown(user.id, "termination"))
        chatter
      }
    )
    ref
  }

  override def onSignal: PartialFunction[Signal, Behavior[BotCommand]] = {
    case PostStop | PreRestart =>
      shutdown()
      Behaviors.stopped
  }

}

object Bot {

  type PerChatStarter = (
      User,
      RequestHandler[Future],
      ActorRef[RequestChatShutdown],
      Option[ActorRef[String]]
  ) => Behavior[PerChatBotCommand]

  /**
    * Make a bot connecting to Telegram servers and handling conversations from users by
    * dispatching input to actors created by `perChatStarter` on demand.
    *
    * @param token the Telegram bot token given by [[https://telegram.me/BotFather BotFather]]
    * @param perChatStarter function to create individual conversation bots
    * @param debugActor if defined, the actor to send debugging information to
    * @return
    */
  def apply(
      token: String,
      perChatStarter: PerChatStarter,
      debugActor: Option[ActorRef[String]]
  ): Behavior[BotCommand] =
    Behaviors.setup(new Bot(_, token, perChatStarter, debugActor))

  sealed trait BotCommand
  private case class IncomingMessage(message: Message) extends BotCommand
  private case class ConnectionShutdown(res: Try[Unit]) extends BotCommand
  case object InitiateGlobalShutdown extends BotCommand

  /**
    * The base of message received during a private conversation by the bot
    */
  sealed trait PerChatBotCommand

  /**
    * Private command received for an individual conversation actor
    *
    * @param command the command without the leading /
    * @param args the space-separated command arguments
    */
  case class PrivateCommand(command: String, args: Seq[String])
      extends PerChatBotCommand

  /**
    * Private message received for an individual conversation actor
    *
    * @param data the textual message
    */
  case class PrivateMessage(data: String) extends PerChatBotCommand

  /**
    * Request that the chat bot is shut down
    *
    * @param reason the reason for the shut down
    */
  case class AnnounceShutdown(reason: String) extends PerChatBotCommand

  /**
    * Request that a particular conversation actor is shutdown. The corresponding
    * actor will be stopped.
    *
    * @param userId the user identifier
    */
  case class RequestChatShutdown(userId: Int, reason: String) extends BotCommand

  /**
    * Parse incoming textual message into a regular message or a command starting by '/'.
    *
    * @param text the incoming textual message
    * @return a `Text` or `Command` object
    */
  private def parseText(
      text: String
  ): PerChatBotCommand =
    if (text.startsWith("/")) {
      val words = text.split(' ')
      PrivateCommand(words.head.substring(1), words.toSeq.tail)
    } else {
      PrivateMessage(text)
    }

}
