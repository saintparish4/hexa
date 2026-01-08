val scala3Version = "3.7.4"

lazy val root = project
  .in(file("."))
  .settings(
    name := "cloudflare-rate-limiter",
    version := "0.1.0",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "0.7.29" % Test
    ),

    // Test configuration
    Test / testOptions += Tests.Argument(TestFrameworks.MUnit, "-b"),

    // Compiler options
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xfatal-warnings"
    )
  )
