val isCI = sys.env.get("CI").contains("true")

val ciScalacOptions = Seq(
  "-Wunused:imports"
)

lazy val settings = Seq(
  resolvers += "jitpack" at "https://jitpack.io",
  organization := "com.github.ppotseluev",
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.13.8",
  Compile / scalaSource := baseDirectory.value / "src/main/scala",
  Test / scalaSource := baseDirectory.value / "src/test/scala",
  ThisBuild / scalafixDependencies += Dependency.organizeImports,
  ThisBuild / semanticdbEnabled := true,
  ThisBuild / semanticdbVersion := scalafixSemanticdb.revision,
  ThisBuild / resolvers += Resolver.mavenLocal,
  useCoursier := false,
  scalacOptions := Seq(
    "-target:jvm-17",
    "-Ymacro-annotations",
    "-language:higherKinds",
    "-Xfatal-warnings",
    "-deprecation"
  ) ++ (if (isCI) ciScalacOptions else Seq.empty),
  libraryDependencies ++= Seq(
    Dependency.kittens,
    Dependency.munit
  ),
  addCompilerPlugin(Dependency.kindProjector),
  assembly / assemblyMergeStrategy := {
    case x if x.contains("io.netty.versions.properties")               => MergeStrategy.concat
    case PathList(ps @ _*) if ps.last endsWith "pom.properties"        => MergeStrategy.first
    case PathList("module-info.class")                                 => MergeStrategy.discard
    case PathList("META-INF", "versions", xs @ _, "module-info.class") => MergeStrategy.discard
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  }
)

lazy val root = project
  .in(file("."))
  .settings(name := "algorate")
  .aggregate(
    `model`,
    `math-utils`,
    `cats-utils`,
    `redis-utils`,
    `strategy`,
    `broker-lib`,
    `brokers`,
    `server`,
    `ta4j-model`,
    `trader-charts`,
    `trader-lib`,
    `trader-app`,
    `tools-app`
  )

lazy val `model` = project
  .settings(
    name := "model",
    settings,
    libraryDependencies ++= Seq(
      Dependency.enumeratrum
    )
  )

lazy val `ta4j-model` = project //TODO rework
  .settings(
    name := "ta4j-model",
    settings,
    libraryDependencies ++= Seq(
      Dependency.ta4j
    )
  )
  .dependsOn(`model`, `math-utils`)

lazy val `trader-charts` = project
  .settings(
    name := "trader-charts",
    settings,
    libraryDependencies ++= Seq(
      Dependency.jfree
    )
  )
  .dependsOn(
    `strategy`, //TODO charts shouldn't depend on strategy
    `ta4j-model`
  )

lazy val `trader-app` = project
  .settings(
    name := "trader-app",
    settings,
    assembly / mainClass := Some("com.github.ppotseluev.algorate.trader.app.AkkaTradingApp")
  )
  .dependsOn(
    `server`
  )

lazy val `math-utils` = project
  .settings(
    name := "math-utils",
    settings,
    libraryDependencies ++= Seq(
      Dependency.apacheMath
    )
  )

lazy val `cats-utils` = project
  .settings(
    name := "cats-utils",
    settings,
    libraryDependencies ++= Seq(
      Dependency.fs2
    )
  )

lazy val `redis-utils` = project
  .settings(
    name := "redis-utils",
    settings,
    libraryDependencies ++= Seq(
      Dependency.boopickle,
      Dependency.redisClient
    ) ++ Dependency.circe.all
  )

lazy val `strategy` = project
  .settings(
    name := "strategy",
    settings
  )
  .dependsOn(`math-utils`, `ta4j-model`)

lazy val `tools-app` = project
  .settings(
    name := "tools-app",
    settings,
    libraryDependencies ++= Seq(
      Dependency.breeze,
      "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.3"
    )
  )
  .dependsOn(
    `server`,
    `trader-charts`
  )

lazy val `server` = project
  .settings(
    name := "server",
    settings,
    libraryDependencies ++= Seq(
      Dependency.protobuf,
      Dependency.logback,
      Dependency.pureconfig
    )
  )
  .dependsOn(
    `brokers`,
    `trader-lib`
  )

lazy val `broker-lib` = project
  .settings(
    name := "broker-lib",
    settings,
    libraryDependencies ++= Seq(
      Dependency.upperbound,
      Dependency.scalaLogging
    ) ++ Dependency.kantanCsv.all
  )
  .dependsOn(
    `model`,
    `math-utils`,
    `redis-utils`,
    `cats-utils`
  )

lazy val `brokers` = project
  .settings(
    name := "brokers",
    settings,
    libraryDependencies ++= Seq(
      Dependency.tinkoffInvestApi,
      Dependency.binanceClient,
      "com.github.binance-exchange" % "binance-java-api" % "master-SNAPSHOT"
    )
  )
  .dependsOn(`broker-lib`)

lazy val `trader-lib` = project
  .settings(
    name := "trader-lib",
    settings,
    libraryDependencies ++= Seq(
      Dependency.akka,
      Dependency.fs2
    ) ++ Dependency.sttp.all ++ Dependency.httpServer.all
  )
  .dependsOn(
    `trader-charts`,
    `brokers` //TODO should depend only on broker-lib, not impl
  )
