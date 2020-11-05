import sbt._

scalaVersion := "2.12.12"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.6.10",
  "com.typesafe.akka" %% "akka-stream" % "2.6.10",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.10",
  "com.typesafe.akka" %% "akka-http" % "10.2.1",
  "de.heikoseeberger" %% "akka-http-play-json" % "1.35.0",
  "org.apache.pdfbox" % "pdfbox" % "2.0.21",
  "io.nayuki" % "qrcodegen" % "1.6.0",
  "commons-io" % "commons-io" % "2.8.0",
  "org.apache.commons" % "commons-lang3" % "3.11",
  "com.bot4s" %% "telegram-core" % "4.4.0-RC2",
  "com.bot4s" %% "telegram-akka" % "4.4.0-RC2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.github.tototoshi" %% "scala-csv" % "1.3.6",
  "org.specs2" %% "specs2-core" % "4.10.5" % "test",
  "com.google.zxing" % "core" % "3.4.1" % "test",
  "com.google.zxing" % "javase" % "3.4.1" % "test",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.10" % Test
)

assemblyOutputPath in assembly := file("ausweis.jar")

test in assembly := {}

assemblyMergeStrategy in assembly := {
  case "module-info.class" => MergeStrategy.discard // Necessary for jackson
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

lazy val genCommands = taskKey[Unit]("Generate commands.txt for BotFather help")
fullRunTask(genCommands, Compile, "Ausweis", "--gen-commands", "commands.txt")
fork in genCommands := true