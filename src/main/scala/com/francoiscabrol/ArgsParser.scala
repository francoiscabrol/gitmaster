package com.francoiscabrol

import scala.collection.mutable.ListBuffer

package object ArgsParser {

  private val actionsList = ListBuffer[Action]()
  private val paramsList = ListBuffer[Param[_]]()

  def actions = actionsList.toList
  def params = paramsList.toList
  def register(obj: Action): Action = {
    actionsList += obj
    obj
  }
  def register[T](obj: Param[T]): Param[T] = {
    paramsList += obj
    obj
  }

  case class Param[T](description: String, cmd: String, defaultValue: T) {

    private var _value: Option[T] = None

    def value: T = _value.getOrElse(defaultValue)
    def value_=(nval:T): Unit = _value = Some(nval)
  }

  case class Action(description: String, cmd: String, nargs: Int = 0, var args:Array[String] = Array(), task: (Array[String]) => Unit) {

    def contract: Unit = {
      require(args.size == nargs, s"The action $cmd require $nargs arguments. See help.")
    }

    def execute: Unit = task(args)

  }

  def parseArgs(args: Array[String], defaultAction: Action, actions: Set[Action] = Set(), options: Set[Param[_]] = Set()): (Set[Action], Set[Param[_]]) = {
    def addParam[T](param: Param[T]) = {
      param match {
        case p: Param[T] if p.defaultValue.isInstanceOf[String] => {
          val param = p.asInstanceOf[Param[String]]
          param.value = args(1)
          parseArgs(args.drop(2), defaultAction, actions, options + param)
        }
        case p:Param[T] if p.defaultValue.isInstanceOf[Boolean] => {
          val param = p.asInstanceOf[Param[Boolean]]
          param.value = !param.defaultValue
          parseArgs(args.drop(1), defaultAction, actions, options + param)
        }
      }
    }
    def addAction(action: Action) = {
      val actionArguments = args.take(action.nargs + 1).drop(1)
      action.args = actionArguments
      action.contract
      parseArgs(args.drop(1 + action.nargs), defaultAction, actions + action, options)
    }

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

