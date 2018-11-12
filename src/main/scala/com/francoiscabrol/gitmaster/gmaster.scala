package com.francoiscabrol.gitmaster

import java.io.File
import java.util.concurrent.TimeoutException
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import com.francoiscabrol.ArgsParser
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
    def listDirectories = listDirectoriesRecurively(1)
    def listDirectoriesRecurively(level: Int): List[File] = {
      val files = file.listFiles.filter(_.isDirectory).toList
      if (level > 1)
        files ++ files.map(_.listDirectoriesRecurively(level-1)).flatten
      else
        files
    }
    def isGitRepo: Boolean = file.listFiles.exists(f => f.getName == ".git")
    def listGitRepositories(level: Int = 1): (List[File], List[File]) = file.listDirectoriesRecurively(level).filter(!_.isHidden).partition(_.isGitRepo)
  }

  /*
  * PARAMS
  */
  val Dir = ArgsParser register new Param[String](
    description = "Directory where to execute the actions. Ex: gmaster --dir ~/Workpace. By default, it is the current directory.",
    cmd = "--dir",
    defaultValue = "."
  )

  val RecursiveLevel = ArgsParser register new Param[String](
    description = "Levels of recursion. Ex: gmaster --level 2. By default, level is set to 1.",
    cmd = "--level",
    defaultValue = "1"
  )

  val InlineStatus = ArgsParser register new Param[Boolean](
    description = "If defined, show the repositories grouped by status. Ex: gmaster status --group. By default, it is inline.",
    cmd = "--group",
    defaultValue = true
  )

  val ShowBranch = ArgsParser register new Param[Boolean](
    description = "If defined, show the branch names. Ex: gmaster status --branch",
    cmd = "--branch",
    defaultValue = false
  )

  /*
  * ACTIONS
  */
  ArgsParser register new Action (
    description = "Dump the list of repositories in the .gitmaster file",
    cmd = "dump",
    task = (args: Array[String]) => {
      val configFile = new File(Dir.value + "/.gitmaster")
      val confirm = if (configFile.exists) readLine("Are you sure that you want to override the file .gitmaster? (yes or no) ") else "yes"
      confirm match {
        case "yes" => {
          Out startWait "Dump the repository list"
          val (gitRepos, notGitRepos) = new File(Dir.value).listGitRepositories(RecursiveLevel.value.toInt)
          val infos = gitRepos map((dir) => {
            Repository.create(dir)
          })
          val repositoryConfigs = ListBuffer[RepositoryConfig]()
          infos.map(info => {
            info.getRemoteUrl match {
              case Success(url) => repositoryConfigs += RepositoryConfig(url, info.branch, info.relativePath(Dir.value))
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
  )

  ArgsParser register new Action (
    description = "Clone all repositories defined in the .gitmaster file",
    cmd = "init",
    task = (args: Array[String]) => {
      Out startWait "Initialiazing"
      val (gitRepos, notGitRepos) = new File(Dir.value).listGitRepositories(RecursiveLevel.value.toInt)
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
        Try(GitCmd.cloneTo(repo.url, repo.branch, repo.relativePath)) match {
          case Success(res) => Out println " Done".green
          case Failure(_) => Out println " Impossible to clone -b " + repo.branch + " "+ repo.url
        }
      })
      Out.stopWait
    }
  )

  ArgsParser register new Action (
    description = "Fetch each repositories",
    cmd = "fetch",
    task = (args: Array[String]) => {
      Out startWait "Fetching the git repositories"
      val (gitRepos, notGitRepos) = new File(Dir.value).listGitRepositories(RecursiveLevel.value.toInt)
      val futures = Future.sequence({
        gitRepos.map(repo => {
          Future {
            Try(GitCmd.fetch(repo)) match {
              case Success(res) => Out print repo println " fetched".green
              case Failure(res) => Out println s"$repo failed to fetch".red println res
            }
          }
        })
      })
      Await.ready(futures, TIMEOUT)
      Out.stopWait println "All repositories fetched."
    }
  )

  val STATUS = ArgsParser register new Action (
    description = "Show the status of each repositories",
    cmd = "status",
    task = (args: Array[String]) => {
      Out startWait "Get the git repositories\' status"
      val (gitRepos, notGitRepos) = new File(Dir.value).listGitRepositories(RecursiveLevel.value.toInt)
      val infos = gitRepos map((dir) => {
        Repository.create(dir)
      })
      if (infos.isEmpty)
        Out.println("No git repository here.")
      else {
        if (InlineStatus.value) {
          val rows = infos.map(repository => {
            if (ShowBranch.value)
              Row(Col(repository.relativePath(Dir.value)), Col(repository.branch), Col(GitStatus.litteralStatus(repository.status)))
            else
              Row(Col(repository.relativePath(Dir.value)), Col(GitStatus.litteralStatus(repository.status)))
          })
          Out.println(Table(rows: _*)).ln
        } else {
          val repositoriesByStatus = infos.groupBy(_.status)
          for ((status, repositories) <- repositoriesByStatus) {
            Out.println(GitStatus.litteralStatus(status))
            val rows = repositories.map(repository => {
              if (ShowBranch.value)
                Row(Col(repository.relativePath(Dir.value)),Col(repository.branch))
              else
                Row(Col(repository.relativePath(Dir.value)))
            })
            Out.println(Table(rows: _*)).ln
          }
        }
      }
      Out.stopWait
    })

  ArgsParser register new Action (
    description = "Pull each repositories",
    cmd = "pull",
    task = (args: Array[String]) => {
      val (gitRepos, notGitRepos) = new File(Dir.value).listGitRepositories(RecursiveLevel.value.toInt)
      val infos = gitRepos map((dir) => {
        Repository.create(dir)
      })
      if (infos.isEmpty)
        Out println "No git repository here."
      infos.foreach((info) => {
        if (info.status == GitStatus.NOT_SYNC) {
          Out print info.name print " pulling ".red
          Try(GitCmd.pull(info.dir)) match {
            case Success(_) => Out println "Done".green
            case Failure(_) => Out println "Fail".red
          }
        } else {
          Out print info.name println " Already sync".green
        }
      })
    }
  )

  ArgsParser register new Action (
    description = "Clone the repository",
    cmd = "clone",
    nargs = 1,
    task = (args: Array[String]) => {
      val repo = args(0)
      Out startWait "Cloning " + repo
      val foldersBefore = new File(Dir.value).listDirectories
      Try(GitCmd.clone(repo)) match {
        case Success(res) => {
          Out stopWait
          val configFile = new File(Dir.value + "/.gitmaster")
          val config = if (configFile.exists) ConfigFile.load(configFile) else ConfigFile(file = configFile)
          val foldersAfter = new File(Dir.value).listDirectories
          val newFolders = foldersAfter.diff(foldersBefore)
          assume(newFolders.size == 1)
          val repoDir = newFolders.head
          println(repoDir)
          val repoInfo = Repository.create(repoDir)
          repoInfo.getRemoteUrl match {
            case Success(url) => {
              config.repositories = config.repositories ::: List(RepositoryConfig(url, repoInfo.branch, repoInfo.relativePath(Dir.value)))
              config.write
              Out println "Done".green
            }
            case Failure(e:GitMasterError) => Out println "[WARNING] " + repoInfo.name + ": " + e.message
            case Failure(e) => Out println "[ERROR] " +  repoInfo.name + ": " + e.toString
          }
        }
        case Failure(_) => Out stopWait; Out println "Impossible to clone " + repo
      }
      Out stopWait
    }
  )

  ArgsParser register new Action(
    description = "Clone the template and initialize it. Ex: gmaster template $templateUrl",
    cmd = "template",
    nargs = 1,
    task = (args: Array[String]) => {
      val templateUrl = args(0)
      val dest = readLine("Name? ")
      Out.startWait("Cloning " + templateUrl)
      Try(GitCmd.cloneTo(templateUrl, dest)) match {
        case Success(res) => {
          Out.stopWait.startWait("Initialize the template")
          FileUtils.deleteDirectory(new File(s"$dest/.git"))
          Out.println(s"The template $templateUrl was cloned and initialized.")
        }
        case Failure(_) => Out.stopWait.println("Impossible to clone the template.")
      }
      Out.stopWait
    }
  )

  ArgsParser register new Action(
    description = "Show this help",
    cmd = "help",
    task = (args: Array[String]) => {
      Out println "Actions".blue
      Out println Table(actions.map(action => {
        Row(Col(action.cmd), Col(action.description))
      }): _*)
      Out println "Params".blue
      Out println Table(params.map(param => {
        Row(Col(param.cmd), Col(param.description))
      }): _*)
    }
  )

  def main(args: Array[String]) {
    try {
      val (actions, _) = ArgsParser.parseArgs(args, STATUS)
      actions.foreach(_.execute)
    } catch {
      case _: TimeoutException => Out.fatal("Timeout of " + TIMEOUT.toSeconds + " seconds is over.")
      case err: GitMasterError  => Out.error(err.message)
      case err: IllegalArgumentException => Out.error(err.getMessage)
      case err: GitCmdError  => Out.fatal(err.message)
    }
  }
}
