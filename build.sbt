import sbt._

scalaVersion := "2.12.11"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.6.4",
  "com.typesafe.akka" %% "akka-stream" % "2.6.4",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.4",
  "com.typesafe.akka" %% "akka-http" % "10.2.0-M1",
  "de.heikoseeberger" %% "akka-http-play-json" % "1.29.1",
  "org.apache.pdfbox" % "pdfbox" % "2.0.19",
  "com.google.zxing" % "core" % "3.4.0",
  "com.google.zxing" % "javase" % "3.4.0",
  "commons-io" % "commons-io" % "2.6",
  "com.bot4s" %% "telegram-core" % "4.4.0-RC2",
  "com.bot4s" %% "telegram-akka" % "4.4.0-RC2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.github.tototoshi" %% "scala-csv" % "1.3.6",
  "org.specs2" %% "specs2-core" % "4.6.0" % "test",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.4" % Test
)

assemblyOutputPath in assembly := file("ausweis.jar")
