package com.francoiscabrol.gitmaster

import java.io.{BufferedWriter, File, FileWriter}

import scala.io.Source
import scala.util.{Failure, Success, Try}

package object Config {

  case class RepositoryConfig(url:String, branch:String) {
    val name = url.split("/").last.replace(".git", "")
  }
  case class ConfigFile(repositories:List[RepositoryConfig], file: File) {
    def write: Unit = {
      if (repositories.isEmpty)
        throw new GitMasterError("No repository found. Impossible to write the config file " + file.getPath)
      val lines = repositories.map(repo => repo.url + " " + repo.branch)
      val bw = new BufferedWriter(new FileWriter(file))
      bw.write(lines.mkString("\n"))
      bw.close()
    }
  }
  object ConfigFile {
    def load(file: File): ConfigFile = {
      val content = Try(Source.fromFile(file)) match {
        case Success(content) => content
        case Failure(_) => throw new GitMasterError("Failed to load the .gitmaster config file.")
      }
      val list = content.getLines().toList.map(lines => {
        val split = lines.trim.split(" ")
        if (split.size != 2) throw new GitMasterError("The .gitmaster file is not correct.")
        RepositoryConfig(split(0), split(1))
      })
      ConfigFile(list, file)
    }
  }

}
