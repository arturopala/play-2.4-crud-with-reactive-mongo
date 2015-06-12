name := """vessels-mgmt-tool"""

version := "1.0-SNAPSHOT"

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

startYear := Some(2015)

description := "Vessels Management Tool"

scalaVersion := "2.11.6"

developers := List(Developer("arturopala","Artur Opala","opala.artur@gmail.com",url("https://pl.linkedin.com/in/arturopala")))

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  specs2 % Test,
  "com.softwaremill.macwire" %% "macros" % "1.0.1",
  "com.softwaremill.macwire" %% "runtime" % "1.0.1",
  "org.scalatest" %% "scalatest" % "2.2.4" % Test,
  "org.scalacheck" %% "scalacheck" % "1.12.2" % Test

	//"junit" % "junit" % "4.12" % Test,
	//"com.novocode" % "junit-interface" % "0.10" % Test,
	//"org.scalautils" % "scalautils_2.11" % "2.1.3" % Test,
	//"org.scala-lang.modules" %% "scala-xml" % "1.0.3" % Test
)

routesGenerator := play.routes.compiler.InjectedRoutesGenerator

scalariformSettings
