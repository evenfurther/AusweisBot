package com.bot4s.telegram.models

/**
  * This object represents changes in the status of a chat member.
  *
  * @param chat           Chat	      Chat the user belongs to
  * @param from           User	      Performer of the action, which resulted in the change
  * @param date           Integer	    Date the change was done in Unix time
  * @param oldChatMember  ChatMember  Previous information about the chat member
  * @param newChatMember  ChatMember	New information about the chat member
  * @param inviteLink     ChatInviteLink	Optional. Chat invite link, which was used by the user to join the chat; for
  *                                   joining by invite link events only.
  */
case class ChatMemberUpdated(
  chat: Chat,
  from: User,
  date: Int,
  oldChatMember: ChatMember,
  newChatMember: ChatMember,
  inviteLink: Option[ChatInviteLink]
) {

}
