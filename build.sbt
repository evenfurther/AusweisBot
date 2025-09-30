import sbt._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._

scalaVersion := "2.13.17"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.8.8",
  "com.typesafe.akka" %% "akka-stream" % "2.8.8",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.8.8",
  "com.typesafe.akka" %% "akka-http" % "10.5.3",
  "de.heikoseeberger" %% "akka-http-play-json" % "1.39.2",
  "org.apache.pdfbox" % "pdfbox" % "3.0.5",
  "commons-io" % "commons-io" % "2.20.0",
  "org.apache.commons" % "commons-lang3" % "3.19.0",
  "com.bot4s" %% "telegram-core" % "5.8.4",
  "com.bot4s" %% "telegram-akka" % "5.8.4",
  "ch.qos.logback" % "logback-classic" % "1.5.18",
  "com.github.tototoshi" %% "scala-csv" % "2.0.0",
  "org.specs2" %% "specs2-core" % "4.22.0" % "test",
  "com.google.zxing" % "core" % "3.5.3" % "test",
  "com.google.zxing" % "javase" % "3.5.3" % "test",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.8.8" % Test,
  "biz.enef" %% "slogging" % "0.6.2"
)

resolvers += Resolver.url(
  "m-k.mx bot4s/telegram shadow",
  url("https://maven.m-k.mx/")
)(Patterns(Resolver.mavenStyleBasePattern))

assembly / mainClass := Some("Ausweis")

assembly / assemblyOutputPath := file("ausweis.jar")

assembly / test := {}

assembly / assemblyMergeStrategy := {
  case "module-info.class" => MergeStrategy.discard
  case PathList("META-INF", _*) => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly/assemblyMergeStrategy).value
    oldStrategy(x)
}

lazy val genCommands = taskKey[Unit]("Generate commands.txt for BotFather help")
fullRunTask(genCommands, Compile, "Ausweis", "--gen-commands", "commands.txt")
genCommands / fork := true

scalariformAutoformat := true
ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignArguments, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentConstructorArguments, true)
  .setPreference(SpacesWithinPatternBinders, false)
  .setPreference(SpacesAroundMultiImports, false)
