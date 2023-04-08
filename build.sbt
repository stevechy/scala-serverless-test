import sbt.nio.file.FileTreeView
import java.nio.file.Paths
import scala.sys.process.Process

ThisBuild / scalaVersion := "3.2.1"
ThisBuild / organization := "com.slopezerosolutions"
name := "scala-serverless-test"

val circeVersion = "0.14.1"
val monocleVersion = "3.2.0"

lazy val root = project.in(file("."))
lazy val commonui = (crossProject(JSPlatform, JVMPlatform) in file("commonui"))
  .settings(libraryDependencies ++= Seq(
    "dev.optics" %%% "monocle-core" ,
    "dev.optics" %%% "monocle-macro"
  ).map(_ % monocleVersion)   )
lazy val commonuiJS = commonui.js
lazy val commonuiJVM = commonui.jvm
lazy val circeDependencies = Seq(libraryDependencies ++= Seq(
  "io.circe" %%% "circe-core",
  "io.circe" %%% "circe-generic",
  "io.circe" %%% "circe-parser"
).map(_ % circeVersion))
lazy val frontend = (project in file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(commonuiJS)
  .settings(
      Compile / mainClass   := Some("Main"),
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "io.github.buntec" %%% "scala-js-snabbdom" % "0.1.0",
      "org.scala-js" %%% "scalajs-dom" % "2.4.0"
    ),
    circeDependencies)

lazy val frontendJavascriptFiles = taskKey[Option[String]]("The list of checksums to generate and to verify for dependencies.")

lazy val parcelJavascriptFiles = taskKey[Unit]("The parcel javascript files")

Global / parcelJavascriptFiles := {
  (frontend / Compile / fullLinkJS).value
  Process("npx" :: "parcel" :: "build" :: Nil, file("frontend")) ! streams.value.log
}

Global / frontendJavascriptFiles := {
  parcelJavascriptFiles.value
  val javascriptFiles = FileTreeView.default.list(Glob(Paths.get("frontend").toAbsolutePath) / "dist" / "index*.js")
  val filenames = javascriptFiles.collectFirst {
    case (path, attributes) => path.toAbsolutePath.getFileName.toString
  }
  filenames
}

lazy val lambdaPackage = taskKey[Unit]("Lambda package")
lambdaPackage := {
  val packageZip = (backend / Universal / packageBin).value
  val file = backend.base / "target" / "universal" / "lambda.json"
  val basePath = root.base.getAbsoluteFile
  val zipFile = basePath.relativize(packageZip).get.toString
  val contents = s"""{"artifact":"${zipFile}"}"""
  IO.write(file, contents)
  ()
}

lazy val backend = (project in file("backend"))
  .dependsOn(commonuiJVM)
  .enablePlugins(JavaAppPackaging)
  .settings(
    topLevelDirectory := None,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.12.0",
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.2",
      "com.amazonaws" % "aws-lambda-java-events" % "3.11.1",
      "io.github.crac" %  "org-crac" %  "0.1.3"
    ),
    circeDependencies,
    Compile / resourceGenerators += Def.task {
      val file = (Compile / resourceManaged).value / "frontend" / "frontend.properties"
      val filename = (Global / frontendJavascriptFiles).value.head
      val contents = s"frontend.javascript.entrypoint=$filename"
      IO.write(file, contents)
      Seq(file)
    }.taskValue
  )

libraryDependencies ++= Seq(
  "com.lihaoyi" %%% "scalatags" % "0.12.0"
)
