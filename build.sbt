version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.7"

scalacOptions := Seq(
  "-Ymacro-annotations",
  "-language:higherKinds",
  "-Xfatal-warnings",
  "-deprecation"
)

ThisBuild / resolvers += Resolver.mavenLocal

lazy val root = (project in file("."))
  .settings(
    name := "algorate"
  )

useCoursier := false

libraryDependencies ++= Seq(
  "ru.tinkoff.piapi" % "java-sdk-core" % Versions.tinkoffInvestApi,
  "co.fs2" %% "fs2-reactive-streams" % Versions.fs2,
  "ch.qos.logback" % "logback-classic" % Versions.logback,
  "com.typesafe.scala-logging" %% "scala-logging" % Versions.scalaLogging,
  "com.softwaremill.common" %% "tagging" % Versions.tagging,
  "com.beachape" %% "enumeratum" % Versions.enumeratum,
  "com.github.ppotseluev" % "eann" % Versions.eann intransitive(),
  "org.openjfx" % "javafx-controls" % Versions.javafx classifier "mac",
  "org.openjfx" % "javafx-graphics" % Versions.javafx classifier "mac",
  "org.openjfx" % "javafx-base" % Versions.javafx classifier "mac",
  "org.ta4j" % "ta4j-core" % Versions.ta4j,
  "org.ta4j" % "ta4j-examples" % Versions.ta4j,
  "org.typelevel" %% "kittens" % Versions.kittens,
  "org.apache.commons" % "commons-math3" % Versions.apacheMath,
  "org.scalameta" %% "munit" % Versions.munit % Test
)
