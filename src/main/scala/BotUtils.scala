import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, ChildFailed, Terminated}
import com.bot4s.telegram.future.Polling

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

object BotUtils {

  private sealed trait WITControl[+T]
  private case class WITMessage[T](message: T) extends WITControl[T]
  private case object WITIdleTimeout extends WITControl[Nothing]
  private case object WITTimer

  implicit class WithIdleTimeout[T: ClassTag](behavior: Behavior[T]) {

    /**
      * Surround the behavior with a timeout controlling one. All messages are
      * forwarded to the inner behavior. However, if no message appears in the
      * specified delay, `timeout` message wil be sent to `controller`.
      *
      * @param timeout the timeout delay
      * @param controller the actor to send `timeoutMessage` to
      * @param timeoutMessage the message sent in case of timeout
      * @tparam U the type of message `controller` expects
      * @return the enriched behavior
      */
    def withIdleTimeout[U](
        timeout: FiniteDuration,
        controller: ActorRef[U],
        timeoutMessage: U
    ): Behavior[T] =
      Behaviors
        .setup[WITMessage[T]] { context =>
          val inner = context.spawnAnonymous(behavior)
          context.watch(inner)
          Behaviors
            .withTimers[WITControl[T]] { timers =>
              timers.startSingleTimer(WITTimer, WITIdleTimeout, timeout)
              Behaviors
                .receiveMessage[WITControl[T]] {
                  case WITMessage(message) =>
                    inner ! message
                    timers.startSingleTimer(WITTimer, WITIdleTimeout, timeout)
                    Behaviors.same
                  case WITIdleTimeout =>
                    controller ! timeoutMessage
                    Behaviors.same
                }
                .receiveSignal {
                  case (_, ChildFailed(_, t)) =>
                    throw t
                  case (_, Terminated(_)) =>
                    Behaviors.stopped
                }
            }
            .narrow[WITMessage[T]]
        }
        .transformMessages[T] { case msg => WITMessage(msg) }
  }

  /**
    * Extends [[com.bot4s.telegram.future.Polling Polling]] with an idempotent shutdown message, as
    * the default implementation throws an exception  if `shutdown()` is called several times in a row.
    */
  trait IdempotentShutdown extends Polling {
    private[this] var shutdownInitiated: Boolean = false

    override def shutdown(): Unit = {
      if (!shutdownInitiated) {
        super.shutdown()
      }
      shutdownInitiated = true
    }
  }

}
