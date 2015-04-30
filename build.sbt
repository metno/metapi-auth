name := """metapi-auth"""

organization := "no.met.data"

version := "0.1-SNAPSHOT"

publishTo := {
  val nexus = "http://maven.met.no/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"



libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  ws,
 "com.github.nscala-time" %% "nscala-time" % "1.8.0",
 "com.google.guava" % "guava" % "18.0"
)
