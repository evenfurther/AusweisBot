import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, ChildFailed, Terminated}
import com.bot4s.telegram.future.Polling

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag
import scala.collection.mutable.Queue

object BotUtils {

  private sealed trait WITControl[+T]
  private case class WITMessage[T](message: T) extends WITControl[T]
  private case object WITIdleTimeout extends WITControl[Nothing]
  private case object WITTimer

  private sealed trait ThrottleControl[+T]
  private case class ThrottleMessage[T](message: T) extends ThrottleControl[T]
  private case object ThrottleUnblock extends ThrottleControl[Nothing]

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


  implicit class WithTrottling[T: ClassTag](behavior: Behavior[T]) {

    /**
      * Surround the behaviour with a throttling one. Incoming messages
      * are sent to the inner behavior with an inter-arrival delay. They
      * are queued in the meantime. If the queue size is greater than the
      * maximum allowed, the queue is emptied and all messages in it are
      * ignored.
      *
      * @param delay the inter-arrival delay between messages
      * @param maxQueueSize the maximum queue size
      * @return the throttled behavior
      */
    def withThrottling(delay: FiniteDuration, maxQueueSize: Int): Behavior[T] = {
      Behaviors.setup[ThrottleControl[T]] { context =>
        val inner = context.spawnAnonymous(behavior)
        var queue: Queue[T] = Queue()
        var waiting = false
        Behaviors.withTimers { timers =>
          Behaviors.receiveMessage {
            case ThrottleMessage(message) =>
              if (waiting) {
                queue :+ message
                if (queue.size > maxQueueSize)
                  queue.clear()
              } else {
                inner ! message
                timers.startSingleTimer(ThrottleUnblock, delay)
                waiting = true
              }
              Behaviors.same
              case ThrottleUnblock =>
                if (queue.isEmpty)
                  waiting = false
                else {
                  inner ! queue.dequeue()
                  timers.startSingleTimer(ThrottleUnblock, delay)
                }
              Behaviors.same
          }
        }
      }.transformMessages[T] {
        case message => ThrottleMessage(message)
      }
    }
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
