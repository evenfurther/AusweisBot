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

  private[this] val chatters: mutable.Map[ChatId, ActorRef[PrivateMessage]] =
    mutable.Map()

  debugActor.foreach(_ ! "Starting")

  context.pipeToSelf(run())(ConnectionShutdown)

  override def receiveMessage(msg: Message): Future[Unit] = {
    context.self ! IncomingMessage(msg)
    Future.successful()
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
          val chatId = ChatId(user.id)
          findOrSpawnChatterBot(user, chatId) ! PrivateMessage(text)
        }
      }
      Behaviors.same
    case RequestChatShutdown(id) =>
      chatters.remove(id).foreach { ref =>
        context.unwatch(ref)
        context.stop(ref)
      }
      Behaviors.same
  }

  private[this] def findOrSpawnChatterBot(
      user: User,
      chatId: ChatId
  ): ActorRef[PrivateMessage] = {
    val ref = chatters.getOrElseUpdate(
      chatId, {
        val chatter = context.spawn(
          perChatStarter(
            user,
            request,
            context.self.narrow[RequestChatShutdown],
            debugActor
          ),
          s"telegram-user${user.id}"
        )
        context.watchWith(chatter, RequestChatShutdown(chatId))
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
  ) => Behavior[PrivateMessage]

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

  /**
    * Private message received for an individual conversation actor
    *
    * @param data the textual message
    */
  case class PrivateMessage(data: String)

  /**
    * Request that a particular conversation actor is shutdown. The corresponding
    * actor will be stopped.
    *
    * @param chatId the user identifier
    */
  case class RequestChatShutdown(chatId: ChatId) extends BotCommand
}
