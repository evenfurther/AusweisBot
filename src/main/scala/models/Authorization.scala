package models

import java.time.LocalDateTime

case class Authorization(
    output: LocalDateTime,
    made: LocalDateTime,
    reasons: Seq[String]
)

object Authorization {

  /**
    * Dictionary mapping reason and reason aliases (for example "santé" and "promenade" are respective aliases of
    * "sante" and "sport") to their official designation in the QR-code and the Y position in the PDF.
    */
  val reasons: Map[String, (String, Float, String)] = {
    val data: Seq[(String, Float, Seq[String], String)] = Seq(
      ("travail", 578, Seq(), "travail"),
      ("achats", 533, Seq("courses"), "courses"),
      ("sante", 477, Seq("santé", "soins"), "santé"),
      ("famille", 435, Seq("proches"), "famille"),
      ("handicap", 396, Seq(), "handicap"),
      (
        "sport_animaux",
        358,
        Seq("sport", "animaux", "promenade", "sortie"),
        "sport ou animaux"
      ),
      ("convocation", 295, Seq("judiciaire"), "convocation"),
      ("missions", 255, Seq("mission"), "missions"),
      ("enfants", 211, Seq("scolaire", "école"), "enfants")
    )
    data.flatMap {
      case (canonical, y, aliases, pretty) =>
        Seq(canonical -> (canonical, y, pretty)) ++ aliases.map(alias =>
          (alias -> (canonical, y, pretty))
        )
    }.toMap
  }

  /**
    * Make valid reasons unique (for example "sport" and "promenade" are the same reason)
    * by only keeping the first one of a kind.
    *
    * @param rs the sequence of reasons, with possible duplicates
    * @return a sequence of reasons without duplicates
    */
  def unifyValidReasons(rs: Seq[String]): Seq[String] = {
    rs.foldLeft((Seq[String](), Set[String]())) {
        case ((result, done), reason) =>
          val canonical = reasons(reason)._1
          if (done.contains(canonical)) { (result, done) }
          else { (result :+ canonical, done + canonical) }
      }
      ._1
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

}
