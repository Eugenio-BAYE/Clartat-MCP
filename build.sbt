val scala3Version = "3.7.3"

lazy val root = project
  .in(file("."))
  .settings(
    name := "clartat-mcp",
    organization := "com.clartat",
    version := "0.1.0",
    scalaVersion := scala3Version,

    // Dependencies
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % "0.14.6",
      "io.circe" %% "circe-generic" % "0.14.6",
      "io.circe" %% "circe-parser" % "0.14.6"
    ),

    // Compiler options for better error messages and warnings
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-Xfatal-warnings"
    )
  )

// Assembly configuration
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "module-info.class" => MergeStrategy.discard
  case x => MergeStrategy.first
}

assembly / assemblyJarName := "clartat-mcp.jar"

assembly / mainClass := Some("com.clartat.mcp.McpServerApp")