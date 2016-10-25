import sbtassembly.AssemblyPlugin.defaultShellScript

name := "gitmaster"

version := "0.9"

scalaVersion := "2.11.8"

assemblyOption in assembly := (assemblyOption in assembly).value.copy(prependShellScript = Some(defaultShellScript))

assemblyJarName in assembly := s"${name.value}-${version.value}"

assemblyOutputPath in assembly := file("./gmaster")
