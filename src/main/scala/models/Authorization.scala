package models

import java.time.LocalDateTime

case class Authorization(
    output: LocalDateTime,
    made: LocalDateTime,
    reason: String
)

object Authorization {

  /**
    * Dictionary mapping reason and reason aliases (for example "santé" and "promenade" are respective aliases of
    * "sante" and "sport") to their official designation in the QR-code and the (X, Y) position in the PDF. */
  private val reasons: Map[String, (String, Float, Float)] = {
    val data: Seq[(String, Float, Float, Seq[String])] = Seq(
      ("travail", 73, 539, Seq("formation")),
      ("sante", 73, 489, Seq("santé")),
      ("famille", 73, 441, Seq()),
      ("handicap", 73, 384, Seq()),
      ("convocation", 73, 349, Seq()),
      ("missions", 73, 313, Seq("mission")),
      ("transits", 73, 264, Seq("transit")),
      ("animaux", 73, 229, Seq("animal"))
    )
    data.flatMap {
      case (canonical, x, y, aliases) =>
        Seq(canonical -> (canonical, x, y)) ++ aliases.map(alias =>
          (alias -> (canonical, x, y))
        )
    }.toMap
  }

  /**
    * Check if a reason is valid.
    *
    * @param reason the reason
    * @return True if the reason is a valid one
    */
  def valid(reason: String): Boolean = reasons.contains(reason)

  /**
    * Return a canonical reason.
    *
    * @param reason the reason
    * @return the canonical reason, or None */
  def canonical(reason: String): Option[String] =
    reasons.get(reason).map(_._1)

  /**
    * Return the coordinates of the checkbox corresponding to a reason.
    * The reason needs not be canonical.
    *
    * @param reason the reason
    * @return the coordinates on the PDF, or None
    */
  def coordinates(reason: String): Option[(Float, Float)] =
    canonical(reason).flatMap(reasons.get).map(v => (v._2, v._3))
}
