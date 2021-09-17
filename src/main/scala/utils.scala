import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.apache.commons.lang3.StringUtils
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileOutputStream

object utils {

  def dateText(date: LocalDateTime): String =
    date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

  def timeText(date: LocalDateTime): String =
    date.format(DateTimeFormatter.ofPattern("HH':'mm"))

  def isAcceptableString(s: String) =
    StringUtils.isAlphanumericSpace(s) || StringUtils.isAsciiPrintable(s)

  def generateCommandListForBotFather(): String = {
    val commands = Seq(
      ("help", "aide sur les commandes"),
      ("start", "début de la saisie des données à partir d'une base vide"),
      ("privacy", "politique de traitement des données personnells"),
      (
        "data",
        "liste les données personnelles enregistrées dans la base de données"
      ),
      ("vierge", "génère une attestation pré-remplie à signer"),
      (
        "autre",
        "suivi d'une liste de motifs séparés par des + (sans espace), " +
          "et éventuellement d'une heure ou du mot « oubli »"
      ),
      ("a", "met à jour l'adresse en conservant l'identité enregistrée"),
      ("i", "met à jour l'identité en conservant l'adresse enregistrée")
    )
    val actions = models.Authorization.reasons
      .map { case (action, (_, _, _, help)) =>
        (
          action,
          s"$help (peut-être suivi d'une heure de sortie ou du mot « oubli »)"
        )
      }
      .filter(a => StringUtils.isAsciiPrintable(a._1))
    (commands ++ actions).sorted.map { case (c, h) =>
      s"$c - $h\n"
    }.mkString
  }

  def saveCommandListForBotFather(fileName: String) = {
    IOUtils.write(
      generateCommandListForBotFather(),
      new FileOutputStream(new File(fileName)),
      "UTF-8"
    )
  }

}
