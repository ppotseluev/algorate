import sbt._

object Dependency {
  val tinkoffInvestApi = "ru.tinkoff.piapi" % "java-sdk-core" % "1.2"

  val fs2 = "co.fs2" %% "fs2-reactive-streams" % "3.4.0"

  val logback = "ch.qos.logback" % "logback-classic" % "1.2.9"

  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4"

  val enumeratrum = "com.beachape" %% "enumeratum" % "1.7.0"

  val ta4j = "org.ta4j" % "ta4j-core" % "0.14"

  val kittens = "org.typelevel" %% "kittens" % "3.0.0"

  val apacheMath = "org.apache.commons" % "commons-math3" % "3.6.1"

  val munit = "org.scalameta" %% "munit" % "0.7.29" % Test

  val upperbound = "org.systemfw" %% "upperbound" % "0.4.0"

  val redisClient = "dev.profunktor" %% "redis4cats-effects" % "1.2.0"

  val protobuf = "com.google.protobuf" % "protobuf-java-util" % "3.21.2"

  val boopickle = "io.suzaku" %% "boopickle" % "1.4.0"

  val akka = "com.typesafe.akka" %% "akka-actor-typed" % "2.6.20"

  val jfree = "org.jfree" % "jfreechart" % "1.0.17" // TODO update versions

  object circe {
    val version = "0.14.2"
    val core = "io.circe" %% "circe-core" % version
    val parser = "io.circe" %% "circe-parser" % version
    val generic = "io.circe" %% "circe-generic" % version
    val genericExtras = "io.circe" %% "circe-generic-extras" % version
    val all = Seq(core, parser, generic, genericExtras)
  }

  object sttp {
    val version = "3.8.5"
    val clientCore = "com.softwaremill.sttp.client3" %% "core" % version
    val clientCirce = "com.softwaremill.sttp.client3" %% "circe" % version
    val clientHttp4sBackend = "com.softwaremill.sttp.client3" %% "http4s-backend" % version
    val all = Seq(clientCore, clientCirce, clientHttp4sBackend)
  }

  object httpServer {
    val tapirVersion = "1.2.4"
    val http4sVersion = "0.23.13"
    val tapirCore = "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirVersion
    val tapirJsonCirce = "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion
    val tapirHttp4s = "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion
    val tapirPrometheus = "com.softwaremill.sttp.tapir" %% "tapir-prometheus-metrics" % tapirVersion
    val http4sBlaze = "org.http4s" %% "http4s-blaze-server" % http4sVersion
    val all = Seq(tapirCore, tapirJsonCirce, tapirHttp4s, tapirPrometheus, http4sBlaze)
  }

  val kindProjector = "org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full

  val organizeImports = "com.github.liancheng" %% "organize-imports" % "0.6.0"

  val pureconfig = "com.github.pureconfig" %% "pureconfig" % "0.17.2"

}
