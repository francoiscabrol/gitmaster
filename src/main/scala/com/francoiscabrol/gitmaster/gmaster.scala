package com.francoiscabrol.gitmaster

import java.io.File
import java.util.concurrent.TimeoutException

import com.francoiscabrol.ArgsParser._
import com.francoiscabrol.gitmaster.git.{GitCmd, GitCmdError, GitStatus}
import com.francoiscabrol.screen.TablePrinter._
import com.francoiscabrol.screen.Colored._
import com.francoiscabrol.screen.Out
import com.francoiscabrol.gitmaster.Config._

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.StdIn.readLine
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global


object Gmaster {

  val TIMEOUT = 60.seconds

  implicit class FileDecored(file: File) {
    def listDirectories: List[File] = file.listFiles.filter(_.isDirectory).toList
    def isGitRepo: Boolean = file.listFiles.exists(f => f.getName == ".git")
  }

  /*
  * ACTIONS
  */
  case object Dir extends Param {
    val name = "Directory where to execute the actions"
    val cmd = "--dir"
    val defaultValue = "."
  }
  register(Dir)

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
          val infos = gitRepos map((dir) => {
            Repository.create(dir)
          })
          val repositoryConfigs = ListBuffer[RepositoryConfig]()
          infos.map(info => {
            info.getRemoteUrl match {
              case Success(url) => repositoryConfigs += RepositoryConfig(url, info.branch)
              case Failure(e:GitMasterError) => Out println "[WARNING] " + info.name + ": " + e.message
              case Failure(e) => Out println "[ERROR] " +  info.name + ": " + e.toString
            }
          })
          ConfigFile(repositoryConfigs.toList, configFile).write
          Out.stopWait
        }
        case _ => Out println "Dump aborted."
      }
    }
  }
  register(DUMP)

  case object INIT extends Action {
    val name = "Clone all repositories defined in the .gitmaster file"
    val cmd = "init"
    override def execute = {
      Out startWait "Initialiazing"
      val directories = new File(".").listDirectories.filter(!_.isHidden)
      val (gitRepos, notGitRepos) = directories.partition(_.isGitRepo)
      val conf = ConfigFile.load(new File(Dir.value + "/.gitmaster"))
      val infos = gitRepos map((dir) => {
        Repository.create(dir)
      })
      val gitReposNames = infos.map(_.dir.getName)
      val notExistingRepos = conf.repositories.filter(repo => {
        !gitReposNames.contains(repo.name)
      })
      notExistingRepos.foreach(repo => {
        Out print "Cloning " + repo.url + " " + repo.branch
        Try(GitCmd.clone(repo.url, repo.branch)) match {
          case Success(res) => Out println " Done".green
          case Failure(_) => Out println " Impossible to clone -b " + repo.branch + " "+ repo.url
        }
      })
      Out.stopWait
    }
  }
  register(INIT)

  case object FETCH extends Action {
    val name = "git fetch each repositories"
    val cmd = "fetch"
    override def execute = {
      Out startWait "Fetching the git repositories"
      val directories = new File(Dir.value).listDirectories.filter(!_.isHidden)
      val (gitRepos, notGitRepos) = directories.partition(_.isGitRepo)
      val futures:Future[List[String]] = Future.sequence({
        gitRepos.map(repo => {
          Future {
            GitCmd.fetch(repo)
          }
        })
      })
      Await.ready(futures, TIMEOUT)
      Out.stopWait println "All repositories fetched."
    }
  }
  register(FETCH)

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
      val infos = gitRepos map((dir) => {
        Repository.create(dir)
      })
      if (infos.isEmpty)
        Out.println("No git repository here.")
      else
        Out.println(Table(infos.map(_.toRow): _*))
      Out.stopWait
    }
  }
  register(STATUS)

  case object PULL extends Action {
    val name = "git pull each repositories"
    val cmd = "pull"
    override def execute = {
      Out startWait "Pulling each repositories"
      val directories = new File(Dir.value).listDirectories.filter(!_.isHidden)
      val (gitRepos, notGitRepos) = directories.partition(_.isGitRepo)
      val infos = gitRepos map((dir) => {
        Repository.create(dir)
      })
      if (infos.isEmpty)
        println("No git repository here.")
      infos.foreach((info) => {
        if (info.status == GitStatus.NOT_SYNC) {
          Out print info.name print " pulling... ".red
          Try(GitCmd.pull(info.dir)) match {
            case Success(_) => Out println "Done".green
            case Failure(_) => Out println "Fail".red
          }
        }
      })
      Out.stopWait
    }
  }
  register(PULL)

  case object HELP extends Action {
    val name = "Show this help"
    val cmd = "help"
    override def execute = {
      Out println "Actions".blue
      Out println Table(actions.map(action => {
        Row(Col(action.cmd), Col(action.name))
      }):_*)
      Out println "Params".blue
      Out println Table(params.map(param => {
        Row(Col(param.cmd), Col(param.name))
      }):_*)
    }
  }
  register(HELP)

  def main(args: Array[String]) {
    try {
      val (actions, _) = parseArgs(args, STATUS)
      actions.foreach(_.execute)
    } catch {
      case _: TimeoutException => Out.fatal("Timeout of " + TIMEOUT.toSeconds + " seconds is over.")
      case err: GitMasterError  => Out.error(err.message)
      case err: GitCmdError  => Out.fatal(err.message)
    }
  }
}
