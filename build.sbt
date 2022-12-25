lazy val settings = Seq(
  organization := "com.github.ppotseluev",
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.13.7",
  Compile / scalaSource := baseDirectory.value / "src/main/scala",
  Test / scalaSource := baseDirectory.value / "src/test/scala",
//  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0",
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
    `math-utils`,
    `redis-utils`,
    `strategy`,
    `broker`,
    `trader`
  )

lazy val trader = project
  .settings(
    name := "trader",
    settings,
    libraryDependencies ++= Seq(
      Dependency.tinkoffInvestApi,
      Dependency.fs2,
      Dependency.logback,
      Dependency.scalaLogging,
      Dependency.enumeratrum,
      Dependency.munit,
      Dependency.upperbound,
      Dependency.protobuf,
      Dependency.akka
    )
  )
  .dependsOn(
    `strategy`,
    `redis-utils`,
    `broker`
  )

lazy val `math-utils` = project
  .settings(
    name := "math-utils",
    settings,
    libraryDependencies ++= Seq(
      Dependency.apacheMath
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

lazy val `broker` = project
  .settings(
    name := "broker",
    settings,
    libraryDependencies ++= Seq(
//      Dependency.scalaGraph,
//      Dependency.catsCore,
//      Dependency.catsFree
    )
  )
//  .dependsOn(`botgen-model`)
