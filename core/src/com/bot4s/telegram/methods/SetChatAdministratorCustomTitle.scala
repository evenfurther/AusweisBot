package com.bot4s.telegram.methods

import com.bot4s.telegram.models.ChatId

/**
  * Use this method to set a custom title for an administrator in a supergroup promoted by the bot.
  * Returns True on success.
  *
  * @param chatId              Integer or String Unique identifier for the target chat or username of the target channel
  *                            (in the format @channelusername)
  * @param userId              Integer Unique identifier of the target user
  * @param customTitle         String  New custom title for the administrator; 0-16 characters, emoji are not allowed
  */
case class SetChatAdministratorCustomTitle(
                            chatId             : ChatId,
                            userId             : Long,
                            customTitle        : String
                            ) extends JsonRequest[Boolean]

