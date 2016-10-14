import sbt._
import sbt.Keys._

object Build extends sbt.Build {

  val scalaV = "2.11.8"
  val akkaV = "2.4.9"
  val sonicdV = "0.6.2"

  val commonSettings = Seq(
    organization := "build.unstable.sonicd.gauth",
    version := "0.1.0",
    scalaVersion := scalaV,

    resolvers += Resolver.bintrayRepo("ernestrc", "maven"),
    scalacOptions := Seq(
      "-unchecked",
      "-Xlog-free-terms",
      "-deprecation",
      "-encoding", "UTF-8",
      "-target:jvm-1.8"
    ))

  val root: Project = Project("sonicd-gauth", file("."))
    .settings(commonSettings: _*)
    .settings(
      artifactName in(Compile, packageBin) := ((_, _, _) ⇒ "sonicd-gauth.jar"),
      libraryDependencies ++= {
        Seq(
          "build.unstable" %% "sonicd-core" % sonicdV,
          "org.scalatest" %% "scalatest" % "2.2.4" % "test"
        )
      }
    )
}
