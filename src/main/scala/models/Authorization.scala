package models

import java.time.LocalDateTime

import org.apache.commons.lang3.StringUtils

import scala.collection.mutable

case class Authorization(
    output: LocalDateTime,
    made: LocalDateTime,
    reasons: Seq[String])

object Authorization {

  // Canonical name, position on certificate, aliases, preferred display name if any
  // (otherwise the canonical name will be used).
  private val data: Seq[(String, Float, Seq[String], Option[String], String)] =
    Seq(
      (
        "travail",
        579,
        Seq("examen", "formation"),
        None,
        "travail, formation, concours ou examens"),
      (
        "sante",
        546,
        Seq("soins"),
        Some("santé"),
        "rendez-vous de santé, de soins, de prévention (dont vaccination) ou d'achats de produits de santé"),
      (
        "famille",
        512,
        Seq("enfants"),
        None,
        "motif familial impérieux, visite à des personnes vulnérables, garde d'enfants"),
      (
        "handicap",
        478,
        Seq(),
        None,
        "assistance aux personnes en situation de handicap"),
      (
        "judiciaire",
        460,
        Seq("convocation", "droit"),
        None,
        "convocation administrative ou judiciaire, professionnel du droit pour un acte qui ne peut se faire à distance"),
      (
        "missions",
        410,
        Seq("mission"),
        None,
        "mission pour le compte d'un service public"),
      (
        "transit",
        378,
        Seq(),
        None,
        "déplacements liés à des transits ferroviaires, aériens ou en bus pour des déplacements de longue distance"),
      ("animaux", 343, Seq(), None, "besoins des animaux"),
      (
        "courses",
        280,
        Seq("déménagement"),
        None,
        "(confinement uniquement) achats de fournitures professionnelles ou de première nécessité, retraits de commande, livraison à domicile, déménagement"),
      (
        "sport",
        235,
        Seq("promenade"),
        None,
        "(confinement uniquement) promenade individuelle ou avec les membres du foyer, sport individuel"),
      (
        "rassemblement",
        167,
        Seq("religion"),
        None,
        "(confinement uniquement) réunion ou manifestation autorisée sur la voie publique, lieu de culte"),
      (
        "demarche",
        121,
        Seq(),
        Some("démarche"),
        "(confinement uniquement) service public"))

  /**
   * Dictionary mapping reason and reason aliases (for example "santé" and
   * "promenade" are respective aliases of "sante" and "sport") to their
   * official designation in the QR-code and the Y position in the PDF as well
   * as a pretty string and the help string.
   */
  val reasons: Map[String, (String, Float, String, String)] =
    data.flatMap {
      case (canonical, y, aliases, pretty, help) => {
        val display = pretty getOrElse canonical
        val alternatives: mutable.Set[String] =
          mutable.Set(Seq(canonical, display) ++ aliases: _*)
        for (a <- alternatives)
          alternatives += StringUtils.stripAccents(a)
        alternatives.toSeq.map(alias =>
          (alias -> (canonical, y, display, help)))
      }
    }.toMap

  /**
   * Make valid reasons unique (for example "sport" and "promenade" are the
   * same reason) by only keeping the first one of a kind, without trying to
   * get the canonical ones.
   *
   * @param rs
   *   the sequence of reasons, with possible duplicates
   * @return
   *   a sequence of reasons without duplicates
   */
  def unifyReasons(rs: Seq[String]): Seq[String] = {
    rs.foldLeft((Seq[String](), Set[String]())) {
      case ((result, done), reason) =>
        val canonical = reasons(reason)._1
        if (done.contains(canonical)) { (result, done) }
        else { (result :+ reason, done + canonical) }
    }._1
  }

  /**
   * Make valid reasons unique (for example "sport" and "promenade" are the
   * same reason) by only keeping the first one of a kind.
   *
   * @param rs
   *   the sequence of reasons, with possible duplicates
   * @return
   *   a sequence of reasons without duplicates
   */
  def unifyValidReasons(rs: Seq[String]): Seq[String] = {
    unifyReasons(rs).map(reasons(_)._1)
  }

  /**
   * Return a sequence of canonical names for reasons ordered by their y
   * coordinates.
   * @param rs
   *   the sequence of reasons, without duplicates
   * @return
   *   the ordered list of canonical reasons
   */
  def orderedCanonicalValidReasons(rs: Seq[String]): Seq[String] = {
    rs.flatMap(reasons.get)
      .sortBy { case (_, y, _, _) => -y }
      .map(_._1)
  }

  /**
   * Pretty-string to describe a reason.
   *
   * @param reason
   *   the reason
   * @return
   *   the pretty string for this reason
   */
  def prettyReason(reason: String): String = reasons(reason)._3

  /**
   * Help string to describe a reason.
   *
   * @param reason
   *   the reason
   * @return
   *   the help for the reason
   */
  def help(reason: String): String = reasons(reason)._4

  /**
   * List of reasons, their aliases and their help string, sorted by reason
   * then in analphabetical order.
   */
  val reasonsAndAliases: Seq[(String, Seq[String], String)] = {
    data
      .sortBy(-_._2)
      .map {
        case (canonical, _, aliases, pretty, help) =>
          (
            pretty getOrElse canonical,
            aliases.sortBy(StringUtils.stripAccents),
            help)
      }
  }

}
