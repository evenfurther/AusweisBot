import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object utils {

  def dateText(date: LocalDateTime): String =
    date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

  def timeText(date: LocalDateTime): String =
    date.format(DateTimeFormatter.ofPattern("HH'h'mm"))

  def hourText(date: LocalDateTime): String =
    date.format(DateTimeFormatter.ofPattern("HH"))

  def minuteText(date: LocalDateTime): String =
    date.format(DateTimeFormatter.ofPattern("mm"))

}
