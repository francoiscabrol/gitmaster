package com.francoiscabrol.screen

object Out {
  private var waitingMessage:Option[AnimatedWaitingMessage] = None
  private var bufferMessage = new String();

  def startWait(message:String) = {
    stopWait
    waitingMessage = Some(new AnimatedWaitingMessage(message).show)
    bufferMessage = new String
    this
  }
  def stopWait = {
    waitingMessage foreach(_.hide)
    waitingMessage = None
    if (!bufferMessage.isEmpty) {
      println(bufferMessage)
      bufferMessage = new String
    }
    this
  }
  def ln = {
    if (waitingMessage.isDefined) {
      bufferMessage += "\n"
    } else {
      Console.println
    }
    this
  }
  def println(str:Any) = {
    if (waitingMessage.isDefined) {
      bufferMessage += str + "\n"
    } else {
      Console.println(str)
    }
    this
  }
  def print(str:Any) = {
    if (waitingMessage.isDefined) {
      bufferMessage += str
    } else {
      Console.print(str)
    }
    this
  }
  def error(str:Any):Unit = {
    stopWait
    System.err.println(s"[ERROR] $str")
  }
  def fatal(str:Any):Unit = {
    stopWait
    System.err.println(s"[FATAL ERROR] $str")
  }
}
