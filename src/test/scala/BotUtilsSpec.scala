import org.specs2.mutable._

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration._

import BotUtils._

class BotUtilsSpec extends Specification {

  val testKit = ActorTestKit()

  "withThrottling" should {

    "not lose messages if number is reasonable" in {
      val witness = testKit.createTestProbe[Int]()
      val actor = testKit.spawn(
        Behaviors
          .receiveMessage[Int] {
            case i =>
              witness.ref ! i
              Behaviors.same
          }
          .withThrottling(20.milliseconds, 5))
      (1 to 6).foreach(actor ! _)
      (1 to 6).foreach(witness.expectMessage(_))
      witness.expectNoMessage()
      success
    }

    "erase messages if number is excessive" in {
      val witness = testKit.createTestProbe[Int]()
      val actor = testKit.spawn(
        Behaviors
          .receiveMessage[Int] {
            case i =>
              witness.ref ! i
              Behaviors.same
          }
          .withThrottling(20.milliseconds, 5))
      (1 to 9).foreach(actor ! _)
      witness.expectMessage(1)
      // 2 to 6 are queued, 7 erases everything
      witness.expectMessage(8)
      witness.expectMessage(9)
      witness.expectNoMessage()
      success
    }
  }

  "withIdleTimeout" should {

    "timeout when there are no more messages" in {
      val witness = testKit.createTestProbe[Int]()
      val controller = testKit.createTestProbe[String]()
      val actor = testKit.spawn(
        Behaviors
          .receiveMessage[Int] {
            case i =>
              witness.ref ! i
              Behaviors.same
          }
          .withIdleTimeout(50.milliseconds, controller.ref, "idle"))
      (1 to 6).foreach(actor ! _)
      (1 to 5).foreach(witness.expectMessage(_))
      controller.expectNoMessage(5.milliseconds)
      witness.expectMessage(6)
      controller.expectMessage(100.milliseconds, "idle")
      success
    }

  }

}
