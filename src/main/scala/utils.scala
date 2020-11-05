import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.apache.commons.lang3.StringUtils

object utils {

  def dateText(date: LocalDateTime): String =
    date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

  def timeText(date: LocalDateTime): String =
    date.format(DateTimeFormatter.ofPattern("HH':'mm"))

  def isAcceptableString(s: String) =
    StringUtils.isAlphanumericSpace(s) || StringUtils.isAsciiPrintable(s)

}
