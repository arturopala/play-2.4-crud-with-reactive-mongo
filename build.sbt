name := """play-2.4-crud-with-reactive-mongo"""

version := "1.0.1-SNAPSHOT"

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

startYear := Some(2015)

description := "Play 2.4 project seed: Generic CRUD with Macwire and ReactiveMongo"

scalaVersion := "2.11.8"

developers := List(Developer("arturopala","Artur Opala","opala.artur@gmail.com",url("https://pl.linkedin.com/in/arturopala")))

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  specs2 % Test,
  "com.softwaremill.macwire" %% "macros" % "2.2.0",
  "com.softwaremill.macwire" %% "runtime" % "1.0.7",
  "org.reactivemongo" %% "reactivemongo" % "0.11.10",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.11.10",

  "org.scalatest" %% "scalatest" % "2.2.5" % Test,
  "org.scalacheck" %% "scalacheck" % "1.12.2" % Test,
  "org.scalatestplus" %% "play" % "1.4.0-M4" % Test
)

routesGenerator := play.routes.compiler.InjectedRoutesGenerator

scalariformSettings
