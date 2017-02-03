package com.francoiscabrol.gitmaster.git

import java.io.File

import scala.sys.process._

object GitCmd {

  private def run(processBuilder: ProcessBuilder):String = {
    val stdout = new StringBuilder
    val stderr = new StringBuilder
    val status = processBuilder ! ProcessLogger(stdout append _, stderr append _)
    if (status != 0)
      throw new GitCmdError(stderr.toString)
    stdout.toString
  }
  def branch(dir: File):String = {
    run(Process("git branch", dir) #| "grep \\*").replace("*", "").trim
  }
  def fetch(dir: File):String = {
    run(Process("git fetch", dir))
  }
  def status(dir: File):GitStatus.Value = run(Process("git status", dir)) match {
    case x if x.contains("publish your local") => GitStatus.CHANGES_TO_PUSH
    case x if x.contains("to be committed") => GitStatus.CHANGES_TO_COMMIT
    case x if x.contains("not staged") => GitStatus.UNSTAGE_FILES
    case x if x.contains("up-to-date") => GitStatus.UP_TO_DATE
    case x if x.contains("git pull") => GitStatus.NOT_SYNC
    case x if x.contains("nothing to commit") => GitStatus.NO_REMOTE
    case _ => GitStatus.UNEXPECTED_ERROR
  }
  def pull(dir: File):String = run(Process("git pull", dir))
  def remoteUrl(dir: File):String = run(Process("git remote show origin", dir)).split(" ").find((str) => str.contains(".git") || str.contains("http")) match {
    case Some(url) => url
    case None => throw new GitCmdError("Impossible to parse the remote url of " + dir.getName)
  }
  def clone(url: String, branch: String):String = run(Process("git clone -b " + branch + " " + url))
  def clone(url: String):String = run(Process("git clone " + url))
}
