package com.francoiscabrol.screen

/**
  * Created by francois on 16-10-16.
  */
package object TablePrinter {

  case class Table(rows:Row*) {
    def rtrim(s: String) = s.replaceAll("\\s+$", "")

    override def toString = {
      val longestRow = rows.maxBy(_.length)
      val columsMaxLength = longestRow.cols.zipWithIndex.map {
        case (col, index) => {
          rows.maxBy(_.cols(index).str.length).cols(index).str.length + 1
        }
      }
      rows.map(row => {
        row.cols.zipWithIndex.map({
          case (col, index) => {
            val t = col.str
            if (index == 0)
              " | " + t + " " * (columsMaxLength(index) - t.length)
            else
              t + " " * (columsMaxLength(index) - t.length)
          }
        }).mkString
      }).map(rtrim(_)).mkString("\n")
    }
  }
  case class Row(cols:List[Col]) {
    def add(col:Col) = Row(col :: cols)
    def length = cols.length
  }
  object Row {
    def apply(cols:Col*):Row = {
      Row(cols.toList)
    }

  }
  case class Col(obj:Any) {
    val str:String = obj.toString
  }
}
