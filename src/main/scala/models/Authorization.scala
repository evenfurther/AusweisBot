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
    * "sante" and "sport") to their official designation in the QR-code and the (X, Y) position in the PDF.
    */
  val reasons: Map[String, (String, Float, Float)] = {
    val data: Seq[(String, Float, Float, Seq[String])] = Seq(
      ("travail", 76, 527, Seq()),
      ("courses", 76, 478, Seq()),
      ("sante", 76, 436, Seq("santé")),
      ("famille", 76, 400, Seq()),
      ("sport", 76, 345, Seq("promenade")),
      ("judiciaire", 76, 298, Seq()),
      ("missions", 76, 260, Seq("mission"))
    )
    data.flatMap {
      case (canonical, x, y, aliases) =>
        Seq(canonical -> (canonical, x, y)) ++ aliases.map(alias =>
          (alias -> (canonical, x, y))
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
          else { (result :+ reason, done + canonical) }
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
      .sortBy { case (_, _, y) => -y }
      .map(_._1)
  }

}
