package models

import java.time.LocalDate
import java.time.format.{DateTimeFormatter, ResolverStyle}
import scala.util.{Failure, Success, Try}
import org.apache.commons.lang3.StringUtils

case class PersonalDataBuilder(
    suggestedFirstName: Seq[String],
    suggestedLastName: Seq[String],
    firstName: Option[String],
    lastName: Option[String],
    birthDate: Option[LocalDate],
    birthPlace: Option[String],
    street: Option[String],
    zip: Option[String],
    city: Option[String]) {

  import PersonalDataBuilder._;

  /**
   * Check if all fields have been filled
   *
   * @return
   *   true if all fields have been fields
   */
  private def isComplete =
    firstName.isDefined && lastName.isDefined && birthDate.isDefined && birthPlace.isDefined && street.isDefined && zip.isDefined && city.isDefined

  private def consolidate =
    if (isComplete) Right(toPersonalData) else Left(this)

  /**
   * Information about the first missing field:
   *   - the prompt
   *   - the options to present to the user
   *   - the function to call to enter the information, returns either the
   *     complete data, the builder, or the error
   *
   * @return
   */
  def firstMissingField: (String, Seq[String], String => Either[String, Either[PersonalDataBuilder, PersonalData]]) =
    if (firstName.isEmpty)
      (
        "Entrez votre prénom",
        suggestedFirstName.toSeq,
        s => {
          Right(copy(firstName = Some(s)).consolidate)
        })
    else if (lastName.isEmpty)
      (
        "Entrez votre nom",
        suggestedLastName.toSeq,
        s => {
          Right(copy(lastName = Some(s)).consolidate)
        })
    else if (birthDate.isEmpty)
      (
        "Entrez votre date de naissance (jj/mm/aaaa)",
        Seq(),
        s =>
          checkBirthDate(s) match {
            case Some(error) => Left(error)
            case None =>
              Right(copy(birthDate = Some(parseBirthDate(s))).consolidate)
          })
    else if (birthPlace.isEmpty)
      (
        "Entrez votre ville de naissance",
        Seq(),
        s => {
          Right(copy(birthPlace = Some(s)).consolidate)
        })
    else if (street.isEmpty)
      (
        "Entrez votre adresse de résidence sans le code postal ni la ville",
        Seq(),
        s => Right(copy(street = Some(s)).consolidate))
    else if (zip.isEmpty)
      (
        "Entrez votre code postal",
        Seq(),
        s =>
          checkZipCode(s) match {
            case Some(error) => Left(error)
            case None        => Right(copy(zip = Some(s)).consolidate)
          })
    else if (city.isEmpty)
      (
        "Entrez votre ville",
        citiesFromZipCode(zip.get),
        s => {
          Right(copy(city = Some(s)).consolidate)
        })
    else
      throw new IllegalStateException(
        "A PersonalDataBuilder should not be used when it is complete")

  /**
   * Strip the identity and keep the rest. The previous identity will be kept
   * as suggestion if possible.
   * @param fn
   *   A new first name suggestion
   * @param ln
   *   A new last name suggestion
   */
  private def stripIdentity(fn: String, ln: Option[String]) =
    copy(
      suggestedFirstName = sortUnique(
        Seq(firstName, Some(fn)) ++ suggestedFirstName.map(s => Some(s)): _*),
      suggestedLastName  = sortUnique(
        Seq(lastName, ln) ++ suggestedLastName.map(s => Some(s)): _*),
      firstName          = None,
      lastName           = None,
      birthDate          = None,
      birthPlace         = None)

  /**
   * Strip the address and keep the rest
   */
  private def stripAddress = copy(street = None, zip = None, city = None)

  def strip(
    strip: StrippedProperty,
    suggestedFirstName: String,
    suggestedLastName: Option[String]) = strip match {
    case StripAddress =>
      stripAddress
    case StripBoth =>
      stripIdentity(suggestedFirstName, suggestedLastName).stripAddress
    case StripIdentity =>
      stripIdentity(suggestedFirstName, suggestedLastName)
  }

  /**
   * Build personal data from complete incomplete data
   *
   * @return
   *   the corresponding personal data
   */
  def toPersonalData: PersonalData =
    PersonalData(
      firstName.get,
      lastName.get,
      birthDate.get,
      birthPlace.get,
      street.get,
      zip.get,
      city.get)

  override def toString: String = {
    Seq(
      firstName.map("Prénom : " + _),
      lastName.map("Nom : " + _),
      birthDate.map(d =>
        s"Naissance : le ${d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}${
          birthPlace
            .map(" à " + _)
        }"),
      street.map(s =>
        s"Adresse de résidence : $s${zip.map(z => s" $z${city.map(" " + _)}")}")).flatten.map("- " + _).mkString("\n")
  }
}

object PersonalDataBuilder {

  private def fromData(data: PersonalData): PersonalDataBuilder =
    PersonalDataBuilder(
      Seq(),
      Seq(),
      Some(data.firstName),
      Some(data.lastName),
      Some(data.birthDate),
      Some(data.birthPlace),
      Some(data.street),
      Some(data.zip),
      Some(data.city))

  /**
   * Make builder from personal data.
   *
   * @param data
   *   the original data
   * @param suggestedFirstName
   *   the suggested first name
   * @param suggestedLastName
   *   the suggested last name
   * @param strip
   *   the data to strip
   * @return
   *   a data builder
   */
  def apply(
    data: PersonalData,
    suggestedFirstName: String,
    suggestedLastName: Option[String],
    strip: StrippedProperty) = fromData(data).strip(strip, suggestedFirstName, suggestedLastName)

  def apply(
    suggestedFirstName: String,
    suggestedLastName: Option[String]): PersonalDataBuilder =
    PersonalDataBuilder(
      sortUnique(Some(suggestedFirstName)),
      sortUnique(suggestedLastName),
      None,
      None,
      None,
      None,
      None,
      None,
      None)

  /**
   * Parse a textual date and return a plausible one. Any year in [0, 20] will
   * be added 2000 to it, and any year in [21, 99] will be added 1900 to it. Of
   * course it is best to supply the full year.
   * @param text
   *   the date to parse
   * @return
   *   a plausible date
   */
  def parseBirthDate(text: String): LocalDate = {
    val date = LocalDate.parse(
      text,
      DateTimeFormatter
        .ofPattern("d/M/u")
        .withResolverStyle(ResolverStyle.STRICT))
    if (date.getYear <= 20) {
      date.plusYears(2000)
    } else if (date.getYear <= 100) {
      date.plusYears(1900)
    } else {
      date
    }
  }

  /**
   * Check that a birth date is at the right format.
   *
   * @param text
   *   the birth date
   * @return
   *   the error message to display if the date is invalid
   */
  def checkBirthDate(text: String): Option[String] = {
    Try(parseBirthDate(text)) match {
      case Success(date) =>
        if (date.isAfter(LocalDate.now())) {
          Some(
            "Je doute que vous soyez né(e) dans le futur, merci de saisir une date plausible")
        } else if (date.isBefore(LocalDate.now().minusYears(110))) {
          Some(
            "À plus de 110 ans il n'est pas prudent de sortir, merci de saisir une date plausible")
        } else {
          None
        }
      case Failure(_) =>
        Some("Date non reconnue, veuillez la ressaisir au format attendu")
    }
  }

  /**
   * Check that the zip code is at the right format.
   *
   * @param text
   *   the zip code
   * @return
   *   the error message to display if the zip code is invalid
   */
  private def checkZipCode(text: String): Option[String] = {
    Try(Integer.parseInt(text)) match {
      case Success(_) => None
      case Failure(_) =>
        Some("Format de code postal non reconnu, veuillez le ressaisir")
    }
  }

  private def citiesFromZipCode(zipCode: String): Seq[String] =
    ZipCodes.fromZipCodes.get(zipCode).map(_.sorted).getOrElse(Seq())

  private def sortUnique(choices: Option[String]*): Seq[String] =
    choices.flatten.toSet.toSeq
      .filter(s => StringUtils.isAlphanumericSpace(s))
      .sorted

  sealed trait StrippedProperty
  case object StripAddress extends StrippedProperty
  case object StripBoth extends StrippedProperty
  case object StripIdentity extends StrippedProperty
}
