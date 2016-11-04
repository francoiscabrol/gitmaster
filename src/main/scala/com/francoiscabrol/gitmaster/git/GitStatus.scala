package com.francoiscabrol.gitmaster.git

import com.francoiscabrol.screen.Colored._

object GitStatus extends Enumeration {
  val NOT_SYNC, UP_TO_DATE, UNSTAGE_FILES, UNEXPECTED_ERROR, NO_REMOTE, CHANGES_TO_COMMIT, CHANGES_TO_PUSH = Value

  def litteralStatus(status: Value) = status match {
    case GitStatus.CHANGES_TO_PUSH => "Need to push".red
    case GitStatus.CHANGES_TO_COMMIT => "Need to commit".red
    case GitStatus.UNSTAGE_FILES => "Unstaged files".red
    case GitStatus.UP_TO_DATE => "Up to date".green
    case GitStatus.NOT_SYNC => "Need to pull".red
    case GitStatus.NO_REMOTE => "No remote"
    case _ => "there is something unusual"
  }

}
