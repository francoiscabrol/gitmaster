package com.francoiscabrol.gitmaster

import java.io.{BufferedWriter, File, FileWriter}
import java.util.concurrent.TimeoutException

import com.francoiscabrol.ArgsParser._
import com.francoiscabrol.gitmaster.git.{GitCmd, GitCmdError, GitStatus}
import com.francoiscabrol.gitmaster.screen.TablePrinter._
import com.francoiscabrol.screen.Colored._
import com.francoiscabrol.screen.Out
import com.francoiscabrol.gitmaster.Config._

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.StdIn.readLine
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}


object Gmaster {

  val timeout = 60.seconds

  implicit class FileDecored(file: File) {
    def listDirectories: List[File] = file.listFiles.filter(_.isDirectory).toList
    def isGitRepo: Boolean = file.listFiles.exists(f => f.getName == ".git")
  }

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
      case GitStatus.NO_REMOTE => "Not synchonized with a remote"
      case _ => "well... there is something unusual".red
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

  object RepositoriesFactory {
    def createRepositories(directories: List[File], conf:Option[ConfigFile]=None): List[Repository] = {
      directories map((dir) => {
          Repository.create(dir, conf)
      })
    }
  }


  /*
  * ACTIONS
  */
  case object Dir extends Param {
    val name = "Directory to parse"
    val cmd = "--dir"
    var value = "."
  }

  paramsList += Dir


  /*
  * ACTIONS
  */
  case object DUMP extends Action {
    val name = "Dump the list of repositories in the .gitmaster file"
    val cmd = "dump"
    override def execute = {
      val configFile = new File(Dir.value + "/.gitmaster")
      val confirm = if (configFile.exists) readLine("Are you sure that you want to override the file .gitmaster? (yes or no) ") else "yes"
      confirm match {
        case "yes" => {
          Out startWait "Dump the repository list"
          val directories = new File(Dir.value).listDirectories.filter(!_.isHidden)
          val (gitRepos, notGitRepos) = directories.partition(_.isGitRepo)
          val infos = RepositoriesFactory.createRepositories(gitRepos)
          val repositoryConfigs = ListBuffer[RepositoryConfig]()
          infos.map(info => {
            info.getRemoteUrl match {
              case Success(url) => repositoryConfigs += RepositoryConfig(url, info.branch)
              case Failure(e:GitMasterError) => Out < "[WARNING] " + info.name + ": " + e.message
              case Failure(e) => Out < "[ERROR] " +  info.name + ": " + e.toString
            }
          })
          ConfigFile(repositoryConfigs.toList, configFile).write
          Out.stopWait
        }
        case _ => Out < "Dump aborted."
      }
    }
  }
  case object INIT extends Action {
    val name = "Clone all repositories defined in the .gitmaster file"
    val cmd = "init"
    override def execute = {
      Out startWait "Initialiazing"
      val directories = new File(".").listDirectories.filter(!_.isHidden)
      val (gitRepos, notGitRepos) = directories.partition(_.isGitRepo)
      val conf = ConfigFile.load(new File(Dir.value + "/.gitmaster"))
      val infos = RepositoriesFactory.createRepositories(gitRepos)
      val gitReposNames = infos.map(_.dir.getName)
      val notExistingRepos = conf.repositories.filter(repo => {
        !gitReposNames.contains(repo.name)
      })
      notExistingRepos.foreach(repo => {
        Out << "Cloning " + repo.url + " " + repo.branch
        Try(GitCmd.clone(repo.url, repo.branch)) match {
          case Success(res) => Out < " Done".green
          case Failure(_) => Out < " Impossible to clone -b " + repo.branch + " "+ repo.url
        }
      })
      Out.stopWait < "Done".green
    }
  }
  case object FETCH extends Action {
    val name = "git fetch each repositories"
    val cmd = "fetch"
    override def execute = {
      Out startWait "Fetching the git repositories"
      val directories = new File(Dir.value).listDirectories.filter(!_.isHidden)
      val (gitRepos, notGitRepos) = directories.partition(_.isGitRepo)
      gitRepos.foreach(repo => {
        Try(GitCmd.fetch(repo)) match {
          case Success(_) => Out << "Fetched " < repo.getName
          case Failure(_) => Out << "Impossible to fetch " < repo.getName
        }
      })
      Out.stopWait < "Done".green
    }
  }
  case object STATUS extends Action {
    val name = "Show the status of each repositories"
    val cmd = "status"
    override def execute = {
      Out startWait "Get the git repositories\' status"
      val directories = new File(Dir.value).listDirectories.filter(!_.isHidden)
      val (gitRepos, notGitRepos) = directories.partition(_.isGitRepo)
      //notGitRepos foreach((dir) => {
        //Out < s"$dir is not a Git repo"
      //})
      val infos = RepositoriesFactory.createRepositories(gitRepos)
      Out.stopWait
      if (infos.isEmpty)
        println("No git repository here.")
      else
        println(Table(infos.map(_.toRow): _*))
    }
  }
  case object PULL extends Action {
    val name = "git pull each repositories"
    val cmd = "pull"
    override def execute = {
      Out startWait "Pulling each repositories"
      val directories = new File(Dir.value).listDirectories.filter(!_.isHidden)
      val (gitRepos, notGitRepos) = directories.partition(_.isGitRepo)
      val infos = RepositoriesFactory.createRepositories(gitRepos)
      if (infos.isEmpty)
        println("No git repository here.")
      infos.foreach((info) => {
        if (info.status == GitStatus.NOT_SYNC) {
          Out << info.name << " pulling... ".red
          Try(GitCmd.pull(info.dir)) match {
            case Success(_) => Out < "Done".green
            case Failure(_) => Out < "Fail".red
          }
        }
      })
      Out.stopWait
    }
  }
  case object HELP extends Action {
    val name = "Show this help"
    val cmd = "help"
    override def execute = {
      println(Table(actionsList.map(action => {
        Row(Col(action.cmd), Col(action.name))
      }):_*))
    }
  }

  actionsList ++= List(STATUS, FETCH, INIT, PULL, DUMP, HELP)

  def main(args: Array[String]) {
    try {
      val (actions, _) = parseArgs(args, STATUS)
      actions.foreach(_.execute)
    } catch {
      case _: TimeoutException => Out.fatal("Timeout of " + timeout.toSeconds + " seconds is over.")
      case err: GitMasterError  => Out.error(err.message)
      case err: GitCmdError  => Out.fatal(err.message)
    }
  }
}
