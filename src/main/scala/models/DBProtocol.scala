package models

import akka.actor.typed.ActorRef

object DBProtocol {

  sealed trait DBControl
  sealed trait DBCommand extends DBControl
  case class Save(userId: Long, data: PersonalData) extends DBCommand
  case class Load(userId: Long, replyTo: ActorRef[Option[PersonalData]])
    extends DBCommand
  case class Delete(userId: Long) extends DBCommand

  case class SendReply(
      replyTo: ActorRef[Option[PersonalData]],
      reply: Option[PersonalData]) extends DBControl

}
