name := """vessels-mgmt-tool"""

version := "1.0-SNAPSHOT"

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

startYear := Some(2015)

description := "Vessels Management Tool"

scalaVersion := "2.11.6"

developers := List(Developer("arturopala","Artur Opala","opala.artur@gmail.com",url("https://pl.linkedin.com/in/arturopala")))

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies ++= Seq(
  specs2 % Test,
  "com.softwaremill.macwire" %% "macros" % "1.0.5",
  "com.softwaremill.macwire" %% "runtime" % "1.0.5",
  "org.reactivemongo" %% "reactivemongo" % "0.10.5.0.akka23",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.10.5.0.akka23",

  "org.scalatest" %% "scalatest" % "2.2.5" % Test,
  "org.scalacheck" %% "scalacheck" % "1.12.2" % Test,
  "org.scalatestplus" %% "play" % "1.4.0-M3" % Test
)

routesGenerator := play.routes.compiler.InjectedRoutesGenerator

scalariformSettings
