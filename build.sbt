name := """scala-basic-api"""
organization := "sun"

version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayWeb, ScalafmtPlugin, SbtDotenv, SwaggerPlugin)

scalaVersion := "2.13.16"

coverageExcludedPackages := ".*(dtos|models|repositories|utils|router).*"

libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

libraryDependencies ++= Seq(
  guice,
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test,
  "com.typesafe.play" %% "play-slick" % "5.4.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.4.0",
  "org.postgresql" % "postgresql" % "42.7.7",
  "com.typesafe.play" %% "play-json" % "2.10.7",
  "org.mindrot" % "jbcrypt" % "0.4",
  "org.webjars" % "swagger-ui" % "5.26.2"
)

swaggerDomainNameSpaces := Seq(  "dtos.request.user",
  "dtos.response")