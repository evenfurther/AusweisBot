package models

import akka.actor.typed.ActorRef

object DBProtocol {

  sealed trait DBControl
  sealed trait DBCommand extends DBControl
  case class Save(id: Int, data: PersonalData) extends DBCommand
  case class Load(id: Int, replyTo: ActorRef[Option[PersonalData]])
      extends DBCommand
  case class Delete(id: Int) extends DBCommand

  case class SendReply(
      replyTo: ActorRef[Option[PersonalData]],
      reply: Option[PersonalData]
  ) extends DBControl

}
