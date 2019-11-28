import Dependencies.Modules._
import sbtregressionsuite.RegressionSuiteKeys
import sbtregressionsuite.RegressionSuiteKeys.regression
import kubeyml.deployment._
import kubeyml.deployment.api._
import kubeyml.deployment.plugin.Keys._

name := "reactive-storage"

version := "0.1"

scalaVersion := "2.13.1"

lazy val flerken =
  (project in file("."))
    .settings(noPublishSettings)
    .aggregate(
      workScheduler
    )

lazy val workScheduler = (project in file("scheduler")).settings(
  libraryDependencies ++= Scheduler.dependencies
).settings(compilerSettings)
  .enablePlugins(dockerPlugins: _*)
  .settings(noPublishSettings).settings(dockerCommonSettings)
  .enablePlugins(KubeDeploymentPlugin)
  .settings(deploymentSettings)

lazy val schedulerIntegrationTests = (project in file("scheduler-integration-tests"))
  .settings(
    libraryDependencies ++= SchedulerIntegrationTests.dependencies
  ).settings(compilerSettings)
  .settings(noPublishSettings)
  .dependsOn(workScheduler)
  .enablePlugins(RegressionSuitePlugin)
  .settings(
    Seq(
      RegressionSuiteKeys.dockerImage in regression := "vaslabs/flerken-regression",
      RegressionSuiteKeys.newVersion in regression := version.value,
      RegressionSuiteKeys.testCommand in regression := Seq("sbt" ,"schedulerIntegrationTests/test"),
      RegressionSuiteKeys.dockerNetwork in regression := Some("sandbox_scheduler")
    )
  )

lazy val noPublishSettings = Seq(
  publish := {},
  skip in publish := true,
  publishLocal := {},
  publishArtifact in Test := false
)

lazy val compilerSettings = {
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-language:postfixOps",              //Allow postfix operator notation, such as `1 to 10 toList'
    "-language:implicitConversions",
    "-language:higherKinds",
    "-Ypartial-unification",
    "-Ywarn-dead-code",                  // Warn when dead code is identified.
    "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
    "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
    "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
    "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
    "-Ywarn-numeric-widen",              // Warn when numerics are widened.
    "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals",              // Warn if a local definition is unused.
    "-Ywarn-unused:params",              // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates",            // Warn if a private member is unused.
    "-Ywarn-value-discard",               // Warn when non-Unit expression results are unused.
    "-Ywarn-unused:imports",
    "-Xfatal-warnings"
  )
}

lazy val dockerCommonSettings = Seq(
  version in Docker := version.value,
  maintainer in Docker := "Vasilis Nicolaou",
  dockerBaseImage := "openjdk:8-alpine",
  dockerExposedPorts := Seq(8080, 8558),
  maintainer := "vaslabsco@gmail.com",
  dockerUsername := sys.env.get("CI_PROJECT_PATH") orElse Some("vaslabs"),
  dockerRepository := sys.env.get("CI_REGISTRY"),
  dynverSeparator in ThisBuild := "-",
  dynverVTagPrefix in ThisBuild := false
)

lazy val dockerPlugins = Seq(DockerPlugin, AshScriptPlugin, JavaAppPackaging, UniversalPlugin)
lazy val deploymentName = sys.env.getOrElse("DEPLOYMENT_NAME", "work-scheduler-test")

lazy val deploymentSettings = Seq(
  namespace in kube := "flerken",
  application in kube := deploymentName,
  envs in kube := Map(
    EnvName("AKKA_CLUSTER_BOOTSTRAP_SERVICE_NAME") -> EnvFieldValue("metadata.labels['app']"),
    EnvName("FLERKEN_HOSTNAME") -> EnvFieldValue("status.podIP"),
    EnvName("FLERKEN_NAMESPACE") -> EnvFieldValue("metadata.namespace"),
    EnvName("DISCOVERY_METHOD") -> EnvRawValue("kubernetes-api")
  ),
  resourceLimits := Resource(Cpu(2), Memory(2048 + 256))
)

