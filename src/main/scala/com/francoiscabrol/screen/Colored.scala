package com.francoiscabrol.screen

package object Colored {
  implicit class stringColors(val s: String) {
    import Console._

    def red = RED + s + RESET
    def green = GREEN + s + RESET
    def blue = BLUE + s + RESET
  }
}
