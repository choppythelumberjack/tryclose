import ReleaseTransformations._
import sbtrelease.ReleasePlugin

lazy val `tryclose` =
  (project in file("."))
    .settings(commonSettings)

lazy val commonSettings = ReleasePlugin.extraReleaseCommands ++ Seq(
  releaseCrossBuild := true,
  organization := "com.github.choppythelumberjack",
  scalaVersion := "2.11.12",
  crossScalaVersions := Seq("2.11.12","2.12.4"),
  libraryDependencies ++= Seq(
    "org.scalatest"   %% "scalatest"     % "3.0.4"     % Test,
    "com.h2database"  % "h2"             % "1.4.196"   % Test
  ),
  pgpSecretRing := file("local.secring.gpg"),
  pgpPublicRing := file("local.pubring.gpg"),
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseProcess := {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) =>
        Seq[ReleaseStep](
          checkSnapshotDependencies,
          inquireVersions,
          runClean,
          setReleaseVersion,
          commitReleaseVersion,
          tagRelease,
          publishArtifacts,
          setNextVersion,
          commitNextVersion,
          pushChanges
        )
      case Some((2, 12)) =>
        Seq[ReleaseStep](
          checkSnapshotDependencies,
          inquireVersions,
          runClean,
          setReleaseVersion,
          publishArtifacts
        )
      case _ => Seq[ReleaseStep]()
    }
  },
  pomExtra := (
    <url>http://github.com/getquill/foo</url>
    <scm>
      <connection>scm:git:git@github.com:choppythelumberjack/tryclose.git</connection>
      <developerConnection>scm:git:git@github.com:choppythelumberjack/tryclose.git</developerConnection>
      <url>https://github.com/choppythelumberjack/tryclose</url>
    </scm>
    <developers>
      <developer>
        <id>choppythelumberjack</id>
        <name>Choppy The Lumberjack</name>
        <url>https://github.com/choppythelumberjack</url>
      </developer>
    </developers>)
)
