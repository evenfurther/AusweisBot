package models

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.libs.json._
import scala.util.{Failure, Success, Try}

case class PersonalData(
    firstName: String,
    lastName: String,
    birthDate: LocalDate,
    birthPlace: String,
    street: String,
    zip: String,
    city: String) {

  /**
   * The birth text as formatted on the official form (without leading 0)
   */
  val birthDateText: String =
    birthDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

  /**
   * A textual representation of the full name
   */
  val fullName: String = s"$firstName $lastName"
}

object PersonalData {

  implicit val personalDataOFormats: OFormat[PersonalData] =
    Json.format[PersonalData]

}
