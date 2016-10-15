package gitmaster

import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter

import sys.process._
import scala.io.StdIn.readLine
import scala.io.Source
import scala.concurrent.ExecutionContext.Implicits.global
import concurrent.{Await, Future}
import scala.concurrent.duration._
import java.util.concurrent.TimeoutException

import scala.language.implicitConversions
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import scala.collection.mutable.ListBuffer
import scala.util

case class GitMasterError(message: String) extends Exception(message)

class AnimatedWaitingMessage(message: String) {
  var i = 1

  val thread = new Thread {
    override def run {
      print(".")
      var done = true
      while(done) {
        try {
          Thread.sleep(1000)
          print(".")
          i += 1
        } catch {
          case _: Throwable => done = false
        }
      }
    }
  }

  def show = {
    print(message)
    thread.start
    this
  }

  def hide {
    thread.interrupt()
    print("\r" + " " * (message.length + i) + "\r")
  }
}

object Out {
  var waitingMessage:Option[AnimatedWaitingMessage] = None
  var bufferMessage = new String();

  def startWait(message:String) = {
    stopWait
    waitingMessage = Some(new AnimatedWaitingMessage(message).show)
    bufferMessage = new String
    this
  }
  def stopWait = {
    waitingMessage foreach(_.hide)
    if (!bufferMessage.isEmpty) {
      println(bufferMessage)
      bufferMessage = new String
    }
    this
  }
  def ln = {
    println
    this
  }
  def <(str:Any) = {
    if (waitingMessage.isDefined) {
      bufferMessage += str + "\n"
    } else {
      println(str)
    }
    this
  }
  def <<(str:Any) = {
    if (waitingMessage.isDefined) {
      bufferMessage += str
    } else {
      print(str)
    }
    this
  }
}

object GitStatus extends Enumeration {
  val NOT_SYNC, UP_TO_DATE, UNSTAGE_FILES, UNEXPECTED_ERROR, NO_REMOTE = Value
}

object GitCmd {

  private def run(processBuilder: ProcessBuilder):String = {
    val stdout = new StringBuilder
    val stderr = new StringBuilder
    val status = processBuilder ! ProcessLogger(stdout append _, stderr append _)
    if (status != 0)
      throw new GitMasterError(stderr.toString)
    stdout.toString
  }
  def branch(dir: File):String = {
    run(Process("git branch", dir) #| "grep \\*").replace("*", "").trim
  }
  def fetch(dir: File):String = {
    run(Process("git fetch", dir))
  }
  def status(dir: File):GitStatus.Value = run(Process("git status", dir)) match {
    case x if x.contains("not staged") => GitStatus.UNSTAGE_FILES
    case x if x.contains("up-to-date") => GitStatus.UP_TO_DATE
    case x if x.contains("git pull") => GitStatus.NOT_SYNC
    case x if x.contains("nothing to commit") => GitStatus.NO_REMOTE
    case _ => GitStatus.UNEXPECTED_ERROR
  }
  def pull(dir: File):String = run(Process("git pull", dir))
  def remoteUrl(dir: File):String = run(Process("git remote show origin", dir)).split(" ").find(_.contains(".git")) match {
    case Some(url) => url
    case None => throw new GitMasterError("Impossible to parse the remote url of " + dir.getName)
  }
  def clone(url: String, branch: String):String = run(Process("git clone -b " + branch + " " + url))
}

object Gmaster {

  val timeout = 60.seconds

  implicit class stringColors(val s: String) {
    import Console._

    def red = RED + s + RESET
    def green = GREEN + s + RESET
    def blue = BLUE + s + RESET
  }

  implicit class FileDecored(file: File) {
    def listDirectories: List[File] = file.listFiles.filter(_.isDirectory).toList
    def isGitRepo: Boolean = file.listFiles.exists(f => f.getName == ".git")
  }

  implicit def tupleToString(t: Tuple2[String, String]):String = t.productIterator.mkString

  case class GitRepo(dir: File, branchName: String, status: GitStatus.Value, conf:Option[GitRepoConf]) {
    val name = dir.getName

    def isGoodBranchCheckout:Option[Boolean] = conf match {
      case Some(conf) => Some(branchName == conf.branch)
      case None => None
    }

    def litteralStatus:String = status match {
      case GitStatus.UNSTAGE_FILES => "Need to stage files".red
      case GitStatus.UP_TO_DATE => "Up to date"
      case GitStatus.NOT_SYNC => "Need to pull".blue
      case GitStatus.NO_REMOTE => "Not synchonized with a remote"
      case _ => "well... there is something unusual".red
    }

    def getRemoteUrl: Try[String] = Try(GitCmd.remoteUrl(dir))

    def toRow = Row(Col(dir), Col(branchName.blue), Col(litteralStatus.green))
  }

  def gitInformations(directories: List[File], conf:Option[GitMasterConf]=None): List[GitRepo] = {
    val futures = directories map((dir) => {
        Future {
          val combinedFuture = for {
            r1 <- Future {
              GitCmd.status(dir)
            }
            r2 <- Future {
              GitCmd.branch(dir)
            }
          } yield (r1, r2)
          val (state, branch) = Await.result(combinedFuture, timeout)
          val repoConf = conf match {
            case Some(conf) => conf.repos.find(_.name == dir.getName)
            case None => None
          }
          GitRepo(dir, branch, state, repoConf)
        }
      })
    Await.result(Future.sequence(futures), timeout)
  }

  case class GitRepoConf(url:String, branch:String) {
     val name = url.split("/").last.replace(".git", "")
     def urlToPull:String = url + "#" + branch
  }
  case class GitMasterConf(repos:List[GitRepoConf])
  object GitMasterConf {
    def load:GitMasterConf = {
      val content = Try(Source.fromFile(".gitmaster")) match {
        case Success(content) => content
        case Failure(_) => throw new GitMasterError("Failed to load the .gitmaster config file.")
      }
      val list = content.getLines().toList.map(lines => {
        val split = lines.trim.split(" ")
        if (split.size != 2) throw new GitMasterError("The .gitmaster file is not correct.")
        GitRepoConf(split(0), split(1))
      })
      GitMasterConf(list)
    }
  }

  def cloneAllRepositories(conf:GitMasterConf, infos:List[GitRepo]) {
    val gitReposNames = infos.map(_.dir.getName)
    val notExistingRepos = conf.repos.filter(repo => {
      !gitReposNames.contains(repo.name)
    })
    notExistingRepos.foreach(repo => {
      Out < "Cloning " + repo.urlToPull
      Try(GitCmd.clone(repo.url, repo.branch)) match {
        case Success(res) => "Done"
        case Failure(_) => throw new GitMasterError("Impossible to clone -b " + repo.branch + " "+ repo.url )
      }
      Out << " Done".green
    })
  }

  sealed trait Action {
    val name:String
    val cmd:String
    def execute = {}
  }
  case object DUMP extends Action {
    val name = "Dump the list of repositories in the .gitmaster file"
    val cmd = "dump"
    override def execute = {
      val confirm = readLine("Are you sure that you want to override the file .gitmaster? (yes or no) ")
      confirm match {
        case "yes" => {
          Out startWait "Listing all repositories"
          val directories = new File(Dir.value).listDirectories.filter(!_.isHidden)
          val (gitRepos, notGitRepos) = directories.partition(_.isGitRepo)
          val infos = gitInformations(gitRepos)
          Out.stopWait
          Out startWait "Dumping"
          var confText:ListBuffer[String] = ListBuffer[String]()
          var errors:ListBuffer[String] = ListBuffer[String]()
          infos.map(info => {
            info.getRemoteUrl match {
              case Success(url) => confText += url + " " + info.branchName
              case Failure(e:GitMasterError) => errors += info.name + ": " + e.message
              case Failure(e) => errors += info.name + ": " + e.toString
            }
          })
          Out.stopWait
          val file = new File(Dir.value + "/.gitmaster")
          val bw = new BufferedWriter(new FileWriter(file))
          bw.write(confText.mkString("\n"))
          bw.close()
          if (!errors.isEmpty) {
            Out << "Done with errors".red
            errors foreach println
          } else {
            Out < "Done".green
          }
        }
        case "no" => Out < "aborted"
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
      val conf = GitMasterConf.load
      val infos = gitInformations(gitRepos)
      cloneAllRepositories(conf, infos)
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
    val name = "Show status"
    val cmd = "status"
    override def execute = {
      Out startWait "Finding the git repositories"
      val directories = new File(Dir.value).listDirectories.filter(!_.isHidden)
      val (gitRepos, notGitRepos) = directories.partition(_.isGitRepo)
      val infos = gitInformations(gitRepos)
      Out.stopWait
      if (infos.isEmpty)
        println("No git repository here.")
      else
        println(Table(infos.map(_.toRow): _*))
    }
  }
  case object PULL extends Action {
    val name = "Pull"
    val cmd = "pull"
    override def execute = {
      Out startWait "Pulling the git repositories"
      val directories = new File(Dir.value).listDirectories.filter(!_.isHidden)
      val (gitRepos, notGitRepos) = directories.partition(_.isGitRepo)
      val infos = gitInformations(gitRepos)
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
  case object LIST_REPOS extends Action {
    val name = "List the repositories found in the directories list"
    val cmd = "list"
    override def execute = {
      Out startWait "Finding the git repositories"
      val directories = new File(".").listDirectories.filter(!_.isHidden)
      val (gitRepos, notGitRepos) = directories.partition(_.isGitRepo)
      val infos = gitInformations(gitRepos)
      Out.stopWait
      notGitRepos foreach((dir) => {
        println(dir + " is not a Git repo")
      })
      if (infos.isEmpty)
        println("No git repository here.")
      else
        infos foreach println
    }
  }

  case class Table(rows:Row*) {
    val maxLength = 35
    def truncate(str:String) = if (str.length > maxLength) str.take(maxLength - 6) + "..." else str
    def bound(i:Int) = if (i > maxLength) maxLength else i
    override def toString = {
      val longestRow = rows.maxBy(_.length)
      val columsMaxLength = longestRow.cols.zipWithIndex.map {
        case (col, index) => {
          rows.maxBy(_.cols(index).str.length).cols(index).str.length + 1
        }
      }
      rows.map(row => {
        row.cols.zipWithIndex.map({
          case (col, index) => val t = col.str; t + " " * (columsMaxLength(index) - t.length) + " | "
        }).mkString
      }).mkString("\n")
    }
  }
  case class Row(cols:Col*) {
    def length = cols.length
  }
  case class Col(obj:Any) {
    val str:String = obj.toString
  }

  val actionsList:List[Action] = List(LIST_REPOS, STATUS, FETCH, INIT, PULL, DUMP, HELP)

  case object HELP extends Action {
    val name = "List of commands"
    val cmd = "help"
    override def execute = {
      println(Table(actionsList.map(action => {
        Row(Col(action.cmd), Col(action.name))
      }):_*))
    }
  }

  sealed trait Param {
    val name: String
    val cmd: String
    var value:String
  }
  case object Dir extends Param {
    val name = "Directory to parse"
    val cmd = "--dir"
    var value = "."
  }

  def parseArgs(args: Array[String], actions:Set[Action]=Set(), options:Set[Param]=Set()):(Set[Action], Set[Param]) = {
    def addParam(param: Param) = {
      param.value = args(1)
      parseArgs(args.drop(2), actions, options + param)
    }
    def addAction(action: Action) = parseArgs(args.drop(1), actions + action, options)
    args match {
      case args if args.isEmpty => if (actions.isEmpty) (Set(STATUS), options) else (actions, options)
      case args if args.length > 0 => args(0) match {
        case Dir.cmd => addParam(Dir)
        case _ => actionsList.find(_.cmd == args(0)) match {
          case Some(action) => addAction(action)
          case None => throw new GitMasterError(s"Action or param '${args(0)}' is not valid. See 'gitmaster help.'")
        }
      }
    }
  }

  def main(args: Array[String]) {
    try {
      val (actions, params) = parseArgs(args)
      actions.foreach(_.execute)
    } catch {
      case _: TimeoutException => Out.stopWait < "Timeout of " + timeout.toSeconds + " seconds is over."
      case err: GitMasterError => Out.stopWait < err.message
    }
  }
}

//try {
  //Gmaster.main(args)
//} catch {
  //case _: TimeoutException => Out.stopWait < "Timeout"
  //case err: GitMasterError => Out.stopWait < err.message
//}
