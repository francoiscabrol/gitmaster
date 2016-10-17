package com.francoiscabrol.screen

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
  def error(str:Any):Unit = {
    stopWait
    System.err.println(s"[ERROR] $str")
  }
  def fatal(str:Any):Unit = {
    stopWait
    System.err.println(s"[FATAL ERROR] $str")
  }
}
