package com.francoiscabrol.gitmaster

import java.io.File

import com.francoiscabrol.gitmaster.Config.{ConfigFile, RepositoryConfig}
import com.francoiscabrol.gitmaster.git.{GitCmd, GitStatus}
import com.francoiscabrol.screen.TablePrinter.{Col, Row}
import com.francoiscabrol.screen.Colored._

import scala.util.Try

case class Repository(dir: File, conf:Option[RepositoryConfig]) {
  val name = dir.getName

  def status = GitCmd.status(dir)

  def branch = GitCmd.branch(dir)

  def isGoodBranchCheckout:Option[Boolean] = conf match {
    case Some(conf) => Some(branch == conf.branch)
    case None => None
  }

  def litteralStatus: String = status match {
    case GitStatus.UNSTAGE_FILES => "Need to stage files".red
    case GitStatus.UP_TO_DATE => "Up to date"
    case GitStatus.NOT_SYNC => "Need to pull".blue
    case GitStatus.NO_REMOTE => "No remote"
    case _ => "there is something unusual".red
  }

  def getRemoteUrl: Try[String] = Try(GitCmd.remoteUrl(dir))

  def toRow = Row(Col(dir), Col(branch.blue), Col(litteralStatus.green))
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