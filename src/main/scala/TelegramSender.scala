import TelegramSender._
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.methods._
import com.bot4s.telegram.models._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

private class TelegramSender(
    context: ActorContext[TelegramOutgoingControl],
    client: RequestHandler[Future],
    queueSize: Int
) extends AbstractBehavior[TelegramOutgoingControl](context) {

  implicit val ec: ExecutionContext = context.executionContext

  override def onMessage(
      msg: TelegramOutgoingControl
  ): Behavior[TelegramOutgoingControl] = msg match {
    case SendText(chatId, text, keyboard, parseMode) =>
      val keys = if (keyboard.isEmpty) {
        None
      } else {
        Some(
          ReplyKeyboardMarkup(
            keyboard.map(row => row.map(t => KeyboardButton(t))),
            resizeKeyboard = Some(true),
            oneTimeKeyboard = Some(true)
          )
        )
      }
      context.pipeToSelf(
        client(
          SendMessage(chatId, text, replyMarkup = keys, parseMode = parseMode)
        ).map(_ => ())
      )(res => Sent(res))
      waitForConfirmation
    case SendFile(chatId, file, caption) =>
      val sent =
        for (_ <- client(SendChatAction(chatId, ChatAction.UploadDocument));
             _ <- client(SendDocument(chatId, file, caption = caption)))
          yield ()
      context.pipeToSelf(sent)(Sent)
      waitForConfirmation
    case message =>
      context.log.error(
        s"Received $message while waiting for outgoing message"
      )
      throw new IllegalStateException(s"Received $message")
  }

  private[this] val waitForConfirmation: Behavior[TelegramOutgoingControl] =
    Behaviors.withStash(queueSize) { buffer =>
      Behaviors.receiveMessage {
        case Sent(Success(_)) =>
          buffer.unstashAll(this)
        case Sent(Failure(t)) =>
          context.log.error("Cannot send outgoing Telegram message", t)
          throw t
        case message =>
          buffer.stash(message)
          Behaviors.same
      }
    }
}

object TelegramSender {

  /**
    * Send messages in order. If a message cannot be sent, an exception will be thrown.
    * For example, this can be used to order messages sent to a single client.
    *
    * @param client The RequestHandler to send messages through
    * @param queueSize The maximum queue size
    * @return the corresponding behavior
    */
  def apply(
      client: RequestHandler[Future],
      queueSize: Int = 10
  ): Behavior[TelegramOutgoingData] =
    Behaviors
      .setup[TelegramOutgoingControl] {
        new TelegramSender(_, client, queueSize)
      }
      .narrow[TelegramOutgoingData]

  sealed trait TelegramOutgoingControl

  sealed trait TelegramOutgoingData extends TelegramOutgoingControl
  case class SendText(
      chatId: ChatId,
      text: String,
      keyboard: Seq[Seq[String]] = Seq(),
      parseMode: Option[ParseMode.ParseMode] = None
  ) extends TelegramOutgoingData
  case class SendFile(
      chatId: ChatId,
      file: InputFile,
      caption: Option[String] = None
  ) extends TelegramOutgoingData

  private case class Sent(res: Try[Unit]) extends TelegramOutgoingControl

}
