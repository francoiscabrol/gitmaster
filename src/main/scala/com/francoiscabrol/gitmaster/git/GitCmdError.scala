package com.francoiscabrol.gitmaster.git

case class GitCmdError(message: String) extends Exception(message)
