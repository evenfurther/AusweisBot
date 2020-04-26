package models

import akka.actor.typed.ActorRef

object DBProtocol {

  sealed trait DBControl
  sealed trait DBCommand extends DBControl
  case class Save(userId: Int, data: PersonalData) extends DBCommand
  case class Load(userId: Int, replyTo: ActorRef[Option[PersonalData]])
      extends DBCommand
  case class Delete(userId: Int) extends DBCommand

  case class SendReply(
      replyTo: ActorRef[Option[PersonalData]],
      reply: Option[PersonalData]
  ) extends DBControl

}
