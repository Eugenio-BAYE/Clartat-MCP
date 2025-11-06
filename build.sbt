val scala3Version = "3.7.3"

lazy val root = project
  .in(file("."))
  .settings(
    name := "Clartat",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
    libraryDependencies += "com.softwaremill.chimp" %% "core" % "0.1.6",
    libraryDependencies += "dev.zio" %% "zio" % "2.1.22"
  )
