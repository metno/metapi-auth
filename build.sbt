name := """auth"""

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

javaOptions += "-Djunit.outdir=target/test-report"

ScoverageSbtPlugin.ScoverageKeys.coverageHighlighting := true

ScoverageSbtPlugin.ScoverageKeys.coverageMinimum := 98

ScoverageSbtPlugin.ScoverageKeys.coverageFailOnMinimum := true



ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := """
  <empty>;
  value.ApiResponse;
  ReverseApplication;
  ReverseAssets;
  authorization.Routes;
  views.html;
"""

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  ws,
 "org.postgresql" % "postgresql" % "9.4-1201-jdbc41",
 "com.github.nscala-time" %% "nscala-time" % "1.8.0",
 "com.google.guava" % "guava" % "18.0"
)
