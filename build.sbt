version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.7"

scalacOptions := Seq(
  "-Ymacro-annotations",
  "-language:higherKinds",
  "-Xfatal-warnings",
  "-deprecation"
)

lazy val root = (project in file("."))
  .settings(
    name := "algorate"
  )

libraryDependencies ++= Seq(
  "ru.tinkoff.invest" % "openapi-java-sdk-core" % Versions.tinkoffInvestApi,
  "ru.tinkoff.invest" % "openapi-java-sdk-java8" % Versions.tinkoffInvestApi,
  "co.fs2" %% "fs2-reactive-streams" % Versions.fs2,
  "ch.qos.logback" % "logback-classic" % Versions.logback,
  "com.typesafe.scala-logging" %% "scala-logging" % Versions.scalaLogging,
  "com.softwaremill.common" %% "tagging" % Versions.tagging,
  "com.beachape" %% "enumeratum" % Versions.enumeratum
)
