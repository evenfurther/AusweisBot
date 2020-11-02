package models

import java.time.LocalDateTime

import org.apache.commons.lang3.StringUtils

import scala.collection.mutable

case class Authorization(
    output: LocalDateTime,
    made: LocalDateTime,
    reasons: Seq[String]
)

object Authorization {

  // Canonical name, position on certificate, aliases, preferred display name if any
  // (otherwise the canonical name will be used).
  private val data: Seq[(String, Float, Seq[String], Option[String])] = Seq(
    (
      "travail",
      578,
      Seq(
        "concours",
        "examen",
        "formation",
        "professionnel",
      ),
      None
    ),
    ("achats", 533, Seq(), Some("courses")),
    ("sante", 477, Seq("soins"), Some("santé")),
    ("famille", 435, Seq("proches"), None),
    ("handicap", 396, Seq(), None),
    (
      "sport_animaux",
      358,
      Seq("sport", "animaux", "sortie"),
      Some("promenade")
    ),
    ("convocation", 295, Seq("administratif", "judiciaire"), None),
    ("missions", 255, Seq("mission"), None),
    (
      "enfants",
      211,
      Seq("scolaire", "école", "crèche", "collège", "lycée"),
      None
    )
  )

  /**
    * Dictionary mapping reason and reason aliases (for example "santé" and "promenade" are respective aliases of
    * "sante" and "sport") to their official designation in the QR-code and the Y position in the PDF as well as
    * a pretty string.
    */
  val reasons: Map[String, (String, Float, String)] =
    data.flatMap {
      case (canonical, y, aliases, pretty) => {
        val display = pretty getOrElse canonical
        val alternatives: mutable.Set[String] =
          mutable.Set(Seq(canonical, display) ++ aliases: _*)
        for (a <- alternatives)
          alternatives += StringUtils.stripAccents(a)
        alternatives.toSeq.map(alias => (alias -> (canonical, y, display)))
      }
    }.toMap

  /**
    * Make valid reasons unique (for example "sport" and "promenade" are the same reason)
    * by only keeping the first one of a kind, without trying to get the canonical ones.
    *
    * @param rs the sequence of reasons, with possible duplicates
    * @return a sequence of reasons without duplicates
    */
  def unifyReasons(rs: Seq[String]): Seq[String] = {
    rs.foldLeft((Seq[String](), Set[String]())) {
        case ((result, done), reason) =>
          val canonical = reasons(reason)._1
          if (done.contains(canonical)) { (result, done) }
          else { (result :+ reason, done + canonical) }
      }
      ._1
  }

  /**
    * Make valid reasons unique (for example "sport" and "promenade" are the same reason)
    * by only keeping the first one of a kind.
    *
    * @param rs the sequence of reasons, with possible duplicates
    * @return a sequence of reasons without duplicates
    */
  def unifyValidReasons(rs: Seq[String]): Seq[String] = {
    unifyReasons(rs).map(reasons(_)._1)
  }

  /**
    * Return a sequence of canonical names for reasons ordered by their y coordinates.
    * @param rs the sequence of reasons, without duplicates
    * @return the ordered list of canonical reasons
    */
  def orderedCanonicalValidReasons(rs: Seq[String]): Seq[String] = {
    rs.flatMap(reasons.get)
      .sortBy { case (_, y, _) => -y }
      .map(_._1)
  }

  /**
    * Pretty-string to describe a reason.
    *
    * @param reason the reason
    * @return the pretty string for this reason
    */
  def prettyReason(reason: String): String = reasons(reason)._3

  /**
    * List of reasons and their aliases, sorted by reason then in
    * analphabetical order.
    */
  val reasonsAndAliases: Seq[(String, Seq[String])] = {
    import scala.math.Ordering.Implicits.seqDerivedOrdering
    data
      .sortBy(-_._2)
      .map {
        case (canonical, _, aliases, pretty) =>
          (
            pretty getOrElse canonical,
            aliases.sortBy(StringUtils.stripAccents)
          )
      }
  }

}
