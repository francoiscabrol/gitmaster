import sbtassembly.AssemblyPlugin.defaultShellScript
import java.nio.file.{Files, StandardCopyOption}

name := "gitmaster"

scalaVersion := "2.11.8"

enablePlugins(GitVersioning)

git.useGitDescribe := true

git.gitTagToVersionNumber := { tag: String =>
  if(tag.length > 1) Some(tag)
  else None
}

assemblyOption in assembly := (assemblyOption in assembly).value.copy(prependShellScript = Some(defaultShellScript))

assemblyJarName in assembly := s"${name.value}-${version.value}"

assemblyOutputPath in assembly := file("./gmaster")

libraryDependencies += "commons-io" % "commons-io" % "2.5"

val install = taskKey[String]("Install the binary in the /usr/bin/ folder.")
install := {
  val binary = new File("gmaster")
  val dest = new File(System.getProperty("user.home") + "/bin/gmaster")
  IO.delete(dest)
  Files.copy(binary.toPath, dest.toPath, StandardCopyOption.COPY_ATTRIBUTES)
  "Done"
}

sourceGenerators in Compile += Def.task {
  val file = (sourceManaged in Compile).value / "gitmaster" / "BuildInfo.scala"

  IO.write(
    file,
    s"""package gitmaster
       |object BuildInfo {
       |  val Version = "${version.value}"
       |}""".stripMargin
  )

  Seq(file)
}.taskValue
