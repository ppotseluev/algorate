version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.7"

scalacOptions := Seq(
  "-Ymacro-annotations",
  "-language:higherKinds",
  "-Xfatal-warnings",
  "-deprecation",
  "-Wunused:imports"
)

ThisBuild / resolvers += Resolver.mavenLocal

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

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
  "com.beachape" %% "enumeratum" % Versions.enumeratum,
//  "com.github.ppotseluev" % "eann" % Versions.eann intransitive (),
  "org.ta4j" % "ta4j-core" % Versions.ta4j,
  "org.ta4j" % "ta4j-examples" % Versions.ta4j,
  "org.typelevel" %% "kittens" % Versions.kittens,
  "org.apache.commons" % "commons-math3" % Versions.apacheMath,
  "org.scalameta" %% "munit" % Versions.munit % Test,
  "org.systemfw" %% "upperbound" % Versions.upperbound,
  "dev.profunktor" %% "redis4cats-effects" % Versions.redisClient,
  "io.circe" %% "circe-core" % Versions.circe,
  "io.circe" %% "circe-parser" % Versions.circe,
  "io.circe" %% "circe-generic" % Versions.circe,
  "com.google.protobuf" % "protobuf-java-util" % Versions.protobuf,
  "io.suzaku" %% "boopickle" % Versions.boopickle
)
