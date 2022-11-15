import sbt._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._

scalaVersion := "2.12.17"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.6.20",
  "com.typesafe.akka" %% "akka-stream" % "2.6.20",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.20",
  "com.typesafe.akka" %% "akka-http" % "10.2.10",
  "de.heikoseeberger" %% "akka-http-play-json" % "1.39.2",
  "org.apache.pdfbox" % "pdfbox" % "2.0.27",
  "commons-io" % "commons-io" % "2.11.0",
  "org.apache.commons" % "commons-lang3" % "3.12.0",
  "com.bot4s" %% "telegram-core" % "4.4.0-RC2-fix5",
  "com.bot4s" %% "telegram-akka" % "4.4.0-RC2-fix5",
  "ch.qos.logback" % "logback-classic" % "1.4.4",
  "com.github.tototoshi" %% "scala-csv" % "1.3.10",
  "org.specs2" %% "specs2-core" % "4.19.0" % "test",
  "com.google.zxing" % "core" % "3.4.1" % "test",
  "com.google.zxing" % "javase" % "3.4.1" % "test",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.20" % Test
)

resolvers += Resolver.url(
  "m-k.mx bot4s/telegram shadow",
  url("https://maven.m-k.mx/")
)(Patterns(Resolver.mavenStyleBasePattern))

assembly / assemblyOutputPath := file("ausweis.jar")

assembly / test := {}

assembly / assemblyMergeStrategy := {
  case "module-info.class" => MergeStrategy.discard // Necessary for jackson
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
