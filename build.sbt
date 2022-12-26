lazy val settings = Seq(
  organization := "com.github.ppotseluev",
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.13.7",
  Compile / scalaSource := baseDirectory.value / "src/main/scala",
  Test / scalaSource := baseDirectory.value / "src/test/scala",
  ThisBuild / scalafixDependencies += Dependency.organizeImports,
  ThisBuild / semanticdbEnabled := true,
  ThisBuild / semanticdbVersion := scalafixSemanticdb.revision,
  ThisBuild / resolvers += Resolver.mavenLocal,
  useCoursier := false,
  scalacOptions := Seq(
    "-Ymacro-annotations",
    "-language:higherKinds",
    "-Xfatal-warnings",
    "-deprecation",
    "-Wunused:imports"
  ),
  libraryDependencies ++= Seq(
    Dependency.kittens
  ),
  addCompilerPlugin(Dependency.kindProjector)
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "algorate"
  )
  .aggregate(
    `model`,
    `math-utils`,
    `redis-utils`,
    `strategy`,
    `broker`,
    `server`,
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

lazy val `trader-lib` = project
  .settings(
    name := "trader-lib",
    settings,
    libraryDependencies ++= Seq(
      Dependency.ta4j
    )
  )
  .dependsOn(`model`)

lazy val `trader-charts` = project
  .settings(
    name := "trader-charts",
    settings,
    libraryDependencies ++= Seq(
      Dependency.jfree
    )
  )
  .dependsOn(
    `strategy`,
    `trader-lib`
  )

lazy val `trader-app` = project
  .settings(
    name := "trader-app",
    settings,
    libraryDependencies ++= Seq(
      Dependency.fs2,
      Dependency.akka
    )
  )
  .dependsOn(
    `server`,
    `trader-charts`
  )

lazy val `math-utils` = project
  .settings(
    name := "math-utils",
    settings,
    libraryDependencies ++= Seq(
      Dependency.apacheMath,
      Dependency.munit
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
    settings,
    libraryDependencies ++= Seq(
      Dependency.ta4j
    )
  )
  .dependsOn(`math-utils`)

lazy val `tools-app` = project
  .settings(
    name := "tools-app",
    settings
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
      Dependency.logback
    )
  )
  .dependsOn(
    `broker`
  )

lazy val `broker` = project
  .settings(
    name := "broker",
    settings,
    libraryDependencies ++= Seq(
      Dependency.tinkoffInvestApi,
      Dependency.upperbound,
      Dependency.scalaLogging
    )
  )
  .dependsOn(
    `model`,
    `math-utils`,
    `redis-utils`
  )
