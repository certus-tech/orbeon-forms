import sbt.Keys._

val ScalaVersion         = "2.11.8"
val ScalaJsDomVersion    = "0.9.0"
val ScalaJsJQueryVersion = "0.9.0"

val DefaultOrbeonFormsVersion   = "2016.2-SNAPSHOT"
val DefaultOrbeonEdition        = "CE"

val ExplodedWarWebInf             = "build/orbeon-war/WEB-INF"
val ExplodedWarLibPath            = ExplodedWarWebInf + "/lib"

val ExplodedWarResourcesPath      = ExplodedWarWebInf + "/resources"
val FormBuilderResourcesPathInWar = "forms/orbeon/builder/resources"

val ScalaJSFileNameFormat = "((.+)-(fastopt|opt)).js".r

val fastOptJSToExplodedWar   = taskKey[Unit]("Copy fast-optimized JavaScript files to the exploded WAR.")
val fullOptJSToExplodedWar   = taskKey[Unit]("Copy full-optimized JavaScript files to the exploded WAR.")

val orbeonVersionFromProperties = settingKey[String]("Orbeon Forms version from system properties.")
val orbeonEditionFromProperties = settingKey[String]("Orbeon Forms edition from system properties.")

// TBH I don't know whether `in ThisBuild` is needed
orbeonVersionFromProperties in ThisBuild := sys.props.get("orbeon.version") getOrElse DefaultOrbeonFormsVersion
orbeonEditionFromProperties in ThisBuild := sys.props.get("orbeon.edition") getOrElse DefaultOrbeonEdition

val copyJarToExplodedWar = taskKey[File]("Copy JAR file to local WEB-INF/bin for development")

def copyScalaJSToExplodedWar(sourceFile: File, rootDirectory: File): Unit = {

  val (prefix, optType) =
    sourceFile.name match { case ScalaJSFileNameFormat(_, prefix, optType) ⇒ prefix → optType }

  val launcherName  = s"$prefix-launcher.js"
  val jsdepsName    = s"$prefix-jsdeps.js"
  val sourceMapName = s"${sourceFile.name}.map"

  val targetDir =
    rootDirectory / ExplodedWarResourcesPath / FormBuilderResourcesPathInWar / "scalajs"

  IO.createDirectory(targetDir)

  val names = List(
    sourceFile                                 → s"$prefix.js",
    (sourceFile.getParentFile / launcherName)  → launcherName,
    (sourceFile.getParentFile / jsdepsName)    → jsdepsName,
    (sourceFile.getParentFile / sourceMapName) → sourceMapName
  )

  for ((file, newName) ← names)
    IO.copyFile(
      sourceFile           = file,
      targetFile           = targetDir / newName,
      preserveLastModified = true
    )
}

lazy val commonSettings = Seq(
  organization                  := "org.orbeon",
  version                       := orbeonVersionFromProperties.value,
  scalaVersion                  := ScalaVersion,

  javacOptions  ++= Seq(
    "-encoding", "utf8",
    "-source", "1.6",
    "-target", "1.6"
  ),
  scalacOptions ++= Seq(
    "-encoding", "utf8",
    "-feature",
    "-language:postfixOps",
    "-language:reflectiveCalls",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-language:existentials"
    // Consider the following flags
//    "-deprecation",
//    "-unchecked",
//    "-Xfatal-warnings",
//    "-Xlint",
//    "-Yno-adapted-args",
//    "-Ywarn-dead-code",        // N.B. doesn't work well with the ??? hole
//    "-Ywarn-numeric-widen",
//    "-Ywarn-value-discard",
//    "-Xfuture",
//    "-Ywarn-unused-import"     // 2.11 only
  ),

  copyJarToExplodedWar := {

    val sourceJarFile = (packageBin in Compile).value
    val targetJarFile = new File(ExplodedWarLibPath + '/' + sourceJarFile.name)

    println(s"Copying JAR ${sourceJarFile.name} to ${targetJarFile.absolutePath}")
    IO.copy(List(sourceJarFile → targetJarFile), overwrite = false, preserveLastModified = false)

    targetJarFile
  }
)

lazy val formBuilderShared = (crossProject.crossType(CrossType.Pure) in file("form-builder-shared"))
  .settings(commonSettings: _*)
  .jvmSettings(
  )
  .jsSettings(
  )

lazy val formBuilderSharedJVM = formBuilderShared.jvm
lazy val formBuilderSharedJS  = formBuilderShared.js

lazy val formBuilderClient = (project in file("form-builder-client"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(formBuilderSharedJS)
  .settings(commonSettings: _*)
  .settings(
    name                           := "form-builder-client",

    scalaSource         in Compile := baseDirectory.value / "src" / "builder" / "scala",
    javaSource          in Compile := baseDirectory.value / "src" / "builder" / "java",
    resourceDirectory   in Compile := baseDirectory.value / "src" / "builder" / "resources",

    jsDependencies                 += RuntimeDOM,

    libraryDependencies            += "org.scala-js" %%% "scalajs-dom"    % ScalaJsDomVersion,
    libraryDependencies            += "be.doeraene"  %%% "scalajs-jquery" % ScalaJsJQueryVersion,

    skip in packageJSDependencies  := false,

    unmanagedBase                  := baseDirectory.value / "lib",

    persistLauncher     in Compile := true,

    fastOptJSToExplodedWar := copyScalaJSToExplodedWar(
      (fastOptJS in Compile).value.data,
      baseDirectory.value.getParentFile
    ),

    fullOptJSToExplodedWar := copyScalaJSToExplodedWar(
      (fullOptJS in Compile).value.data,
      baseDirectory.value.getParentFile
    )
  )

lazy val common = (project in file("common"))
  .settings(commonSettings: _*)
  .settings(
    name          := "orbeon-common",
    unmanagedBase := baseDirectory.value / ".." / "lib"
  )

lazy val xupdate = (project in file("xupdate"))
  .dependsOn(common)
  .settings(commonSettings: _*)
  .settings(
    name          := "orbeon-xupdate",
    unmanagedBase := baseDirectory.value / ".." / "lib"
  )

lazy val dom4j = (project in file("dom4j"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-dom4j"
  )

lazy val dom = (project in file("dom"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-dom"
  )

lazy val formRunner = (project in file("form-runner"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-form-runner"
  )

lazy val formBuilder = (project in file("form-builder"))
  .settings(commonSettings: _*)
  .settings(
    name := "orbeon-form-builder"
  )

val PathsToExcludeFromCoreJAR = List(
  "org/orbeon/oxf/servlet/OrbeonXFormsFilter",
  "org/orbeon/oxf/portlet/OrbeonProxyPortlet",
  "org/orbeon/oxf/fr/embedding/servlet/ServletFilter",
  "org/orbeon/oxf/fr/embedding/servlet/API"
)

lazy val core = (project in file("src"))
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(common, dom, formBuilderSharedJVM, xupdate)
  .settings(commonSettings: _*)
  .settings(
    name                         := "orbeon-core",

    buildInfoPackage             := "org.orbeon.oxf.common",
    buildInfoKeys                := Seq[BuildInfoKey](
      "orbeonVersion" → orbeonVersionFromProperties.value,
      "orbeonEdition" → orbeonEditionFromProperties.value
    ),

    defaultConfiguration := Some(Compile),

    scalaSource       in Compile := baseDirectory.value / "main" / "scala",
    javaSource        in Compile := baseDirectory.value / "main" / "java",
    resourceDirectory in Compile := baseDirectory.value / "main" / "resources",

    scalaSource       in Test    := baseDirectory.value / "test" / "scala",
    javaSource        in Test    := baseDirectory.value / "test" / "java",
    resourceDirectory in Test    := baseDirectory.value / "test" / "resources",

    unmanagedBase                := baseDirectory.value / ".." / "lib",

    // TODO: only src/main/resources/org/orbeon/oxf/xforms/script/coffee-script.js

    // http://www.scala-sbt.org/0.13/docs/Mapping-Files.html
    mappings          in (Compile, packageBin) ~= { _ filterNot { case (_, path) ⇒ PathsToExcludeFromCoreJAR.exists(path.startsWith) } }
  )

lazy val root = (project in file("."))
  .aggregate(common, dom, formBuilderSharedJVM, xupdate, core, formRunner, formBuilder, formBuilderClient)
  .settings(

    scalaVersion                  := ScalaVersion,

    // TEMP: override so that root project it doesn't search under src
    scalaSource       in Compile := baseDirectory.value / "root" / "src" / "main" / "scala",
    javaSource        in Compile := baseDirectory.value / "root" / "src" / "main" / "java",
    resourceDirectory in Compile := baseDirectory.value / "root" / "src" / "main" / "resources",

    scalaSource       in Test    := baseDirectory.value / "root" / "src" / "test" / "scala",
    javaSource        in Test    := baseDirectory.value / "root" / "src" / "test" / "java",
    resourceDirectory in Test    := baseDirectory.value / "root" / "src" / "test" / "resources",

    publishArtifact := false
  )

sound.play(compile in Compile, Sounds.Blow, Sounds.Basso)
