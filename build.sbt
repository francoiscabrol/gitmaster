import sbtassembly.AssemblyPlugin.defaultShellScript
import java.nio.file.{Files, StandardCopyOption}

name := "gitmaster"

version := "0.9"

scalaVersion := "2.11.8"

libraryDependencies += "io.github.francoiscabrol" %% "scala-args-parser" % "0.1.0"

assemblyOption in assembly := (assemblyOption in assembly).value.copy(prependShellScript = Some(defaultShellScript))

assemblyJarName in assembly := s"${name.value}-${version.value}"

assemblyOutputPath in assembly := file("./gmaster")

val install = taskKey[String]("Install the binary in the /usr/bin/ folder.")
install := {
  val binary = new File("gmaster")
  val dest = new File(System.getProperty("user.home") + "/bin/gmaster")
  IO.delete(dest)
  Files.copy(binary.toPath, dest.toPath, StandardCopyOption.COPY_ATTRIBUTES)
  "Done"
}
