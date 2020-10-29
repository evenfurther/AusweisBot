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
  val reasons: Map[String, (String, Float)] = {
    val data: Seq[(String, Float, Seq[String])] = Seq(
      ("travail", 578, Seq()),
      ("achats", 533, Seq("courses")),
      ("sante", 477, Seq("santé", "soins")),
      ("famille", 435, Seq()),
      ("handicap", 396, Seq()),
      ("sport_animaux", 358, Seq("sport", "animaux", "promenade")),
      ("convocation", 295, Seq("judiciaire")),
      ("missions", 255, Seq("mission")),
      ("enfants", 211, Seq("scolaire", "école"))
    )
    data.flatMap {
      case (canonical, y, aliases) =>
        Seq(canonical -> (canonical, y)) ++ aliases.map(alias =>
          (alias -> (canonical, y))
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
      .sortBy { case (_, y) => -y }
      .map(_._1)
  }

}
