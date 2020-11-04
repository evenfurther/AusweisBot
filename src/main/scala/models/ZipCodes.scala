package models

import java.io.StringReader
import java.nio.charset.Charset

import com.github.tototoshi.csv.CSVReader
import org.apache.commons.io.IOUtils

import scala.collection.mutable

object ZipCodes {

  /**
    * Dictionary mapping a zip code to a list of cities sharing the zip code.
    * A single zip code can correspond to multiple cities, and the same city
    * can have several zip codes (Paris, Lyon, Marseille).
   *
   * It uses the database retrieved from [[https://sql.sh/736-base-donnees-villes-francaises]]
   * which is under the CC-BY-SA-4.0 license.
    */
  val fromZipCodes: Map[String, Seq[String]] = {
    val fileContent = new StringReader(
      IOUtils.resourceToString("/villes_france.csv", Charset.forName("UTF-8"))
    )
    val reader = CSVReader.open(fileContent)
    var zipCodes: mutable.Map[String, Seq[String]] = mutable.Map()
    for (line <- reader.iterator if line.length >= 9;
         zipCode <- line(8).split('-')) {
      zipCodes += zipCode -> (zipCodes.getOrElse(zipCode, Seq()) :+ line(5))
    }
    zipCodes.toMap
  }

}
