package com.francoiscabrol.screen

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
