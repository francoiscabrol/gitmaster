package com.francoiscabrol

import scala.collection.mutable.ListBuffer

package object ArgsParser {

  trait Param {
    val name: String
    val cmd: String
    var value: String
  }

  trait Action {
    val name: String
    val cmd: String

    def execute = {}
  }

  val actionsList = ListBuffer[Action]()
  val paramsList = ListBuffer[Param]()

  def parseArgs(args: Array[String], defaultAction: Action, actions: Set[Action] = Set(), options: Set[Param] = Set()): (Set[Action], Set[Param]) = {
    def addParam(param: Param) = {
      param.value = args(1)
      parseArgs(args.drop(2), defaultAction, actions, options + param)
    }
    def addAction(action: Action) = parseArgs(args.drop(1), defaultAction, actions + action, options)

    args match {
      case args if args.isEmpty => actions match {
        case actions if actions.isEmpty => (Set(defaultAction), options)
        case _ => (actions, options)
      }
      case _ => paramsList.find(_.cmd == args(0)) match {
        case Some(param) => addParam(param)
        case None => actionsList.find(_.cmd == args(0)) match {
          case Some(action) => addAction(action)
          case None => throw new IllegalArgumentException(s"Action or param '${args(0)}' is not valid. See help.")
        }
      }
    }
  }
}

