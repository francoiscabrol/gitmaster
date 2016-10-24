package com.francoiscabrol.screen

/**
  * Created by francois on 16-10-16.
  */
package object TablePrinter {

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
          case (col, index) => val t = col.str; " | " + t + " " * (columsMaxLength(index) - t.length)
        }).mkString.trim
      }).mkString("\n")
    }
  }
  case class Row(cols:Col*) {
    def length = cols.length
  }
  case class Col(obj:Any) {
    val str:String = obj.toString
  }
}
