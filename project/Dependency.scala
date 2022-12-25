import sbt._

object Dependency {
  val tinkoffInvestApi = "ru.tinkoff.piapi" % "java-sdk-core" % "1.0.19"

  val fs2 = "co.fs2" %% "fs2-reactive-streams" % "3.2.3"

  val logback = "ch.qos.logback" % "logback-classic" % "1.2.9"

  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4"

  val enumeratrum = "com.beachape" %% "enumeratum" % "1.7.0"

  val ta4j = "org.ta4j" % "ta4j-core" % "0.14"

  val kittens = "org.typelevel" %% "kittens" % "2.3.2"

  val apacheMath = "org.apache.commons" % "commons-math3" % "3.6.1"

  val munit = "org.scalameta" %% "munit" % "0.7.29"

  val upperbound = "org.systemfw" %% "upperbound" % "0.4.0"

  val redisClient = "dev.profunktor" %% "redis4cats-effects" % "1.2.0"

  val protobuf = "com.google.protobuf" % "protobuf-java-util" % "3.21.2"

  val boopickle = "io.suzaku" %% "boopickle" % "1.4.0"

  val akka = "com.typesafe.akka" %% "akka-actor-typed" % "2.6.20"

  object circe {
    val version = "0.14.2"
    val core = "io.circe" %% "circe-core" % version
    val parser = "io.circe" %% "circe-parser" % version
    val generic = "io.circe" %% "circe-generic" % version
    val all = Seq(core, parser, generic)
  }

  val kindProjector = "org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full
}
