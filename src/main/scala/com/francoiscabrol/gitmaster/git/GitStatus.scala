package com.francoiscabrol.gitmaster.git

object GitStatus extends Enumeration {
  val NOT_SYNC, UP_TO_DATE, UNSTAGE_FILES, UNEXPECTED_ERROR, NO_REMOTE, CHANGES_TO_COMMIT = Value
}
