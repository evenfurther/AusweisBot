package com.bot4s.telegram.models

/** This object represents one size of a photo or a file / sticker thumbnail.
  *
  * @param fileId    Identifier for this file
  * @param fileUniqueId Unique identifier for this file
  * @param width     Photo width
  * @param height    Photo height
  * @param fileSize  Optional File size
  */
case class PhotoSize(
                      fileId   : String,
                      fileUniqueId : String,
                      width    : Int,
                      height   : Int,
                      fileSize : Option[Int] = None
                    )
