package com.francoiscabrol.gitmaster

import java.io.File

import com.francoiscabrol.gitmaster.Config.{ConfigFile, RepositoryConfig}
import com.francoiscabrol.gitmaster.git.{GitCmd, GitStatus}
import com.francoiscabrol.screen.TablePrinter.{Col, Row}
import com.francoiscabrol.screen.Colored._

import scala.util.Try

case class Repository(dir: File, conf:Option[RepositoryConfig]) {
  val name = dir.getName

  lazy val status = GitCmd.status(dir)

  def relativePath(relativePath: String) = dir.getPath.diff(relativePath + "/")

  def branch = GitCmd.branch(dir)

  def isGoodBranchCheckout:Option[Boolean] = conf match {
    case Some(conf) => Some(branch == conf.branch)
    case None => None
  }

  def getRemoteUrl: Try[String] = Try(GitCmd.remoteUrl(dir))

  def toRow = Row(Col(GitStatus.litteralStatus(status)), Col(dir), Col(branch))
}
object Repository {
  def create(dir: File, config:Option[ConfigFile]=None): Repository = {
    val repositoryConfig = config match {
      case Some(config) => config.repositories.find(_.name == dir.getName)
      case None => None
    }
    Repository(dir, repositoryConfig)
  }
}
