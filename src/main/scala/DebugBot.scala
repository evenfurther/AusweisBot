import TelegramSender.SendText
import akka.actor.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.clients.AkkaHttpClient
import com.bot4s.telegram.future.TelegramBot
import com.bot4s.telegram.models.ChatId

import scala.concurrent.Future

private class DebugBot(
    context: ActorContext[String],
    token: String,
    chatId: ChatId
) extends AbstractBehavior[String](context)
    with TelegramBot {
  private[this] implicit val system: ActorSystem = context.system.toClassic
  override val client: RequestHandler[Future] = new AkkaHttpClient(token)

  private[this] val sender = context.spawnAnonymous(TelegramSender(client))
  context.watch(sender)

  override def onMessage(msg: String): Behavior[String] = {
    sender ! SendText(chatId, msg)
    Behaviors.same
  }

}

object DebugBot {

  def apply(token: String, chatId: ChatId): Behavior[String] =
    Behaviors.setup(new DebugBot(_, token, chatId))

}
