package models

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.libs.json._

case class PersonalData(
    lastName: String,
    firstName: String,
    birthDate: LocalDate,
    birthPlace: String,
    street: String,
    zip: String,
    city: String
) {

  /**
    * The birth text as formatted on the official form (without leading 0)
    */
  val birthDateText: String =
    birthDate.format(DateTimeFormatter.ofPattern("d/M/yyyy"))

  /**
    * A textual representation of the full name
    */
  val fullName: String = s"$firstName $lastName"
}

object PersonalData {

  implicit val personalDataOFormats: OFormat[PersonalData] =
    Json.format[PersonalData]

}
