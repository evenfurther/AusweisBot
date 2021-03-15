package com.bot4s.telegram.models

/**
  * Represents an invite link for a chat.
  *
  * @param inviteLink   String	The invite link. If the link was created by another chat administrator,
  *                             then the second part of the link will be replaced with “…”.
  * @param creator      User	  Creator of the link
  * @param isPrimary    Boolean	True, if the link is primary
  * @param isRevoked    Boolean	True, if the link is revoked
  * @param expireDate   Integer	Optional. Point in time (Unix timestamp) when the link will expire or has been expired
  * @param memberLimit  Integer	Optional. Maximum number of users that can be members of the chat simultaneously after
  *                             joining the chat via this invite link; 1-99999
  */
case class ChatInviteLink(
  inviteLink: String,
  creator: User,
  isPrimary: Boolean,
  isRevoked: Boolean,
  expireDate: Int,
  memberLimit: Int
) {

}
