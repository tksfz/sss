package org.tksfz.sss

import com.twitter.util.Eval
import java.io.File
import scala.io.Source

class Sss(
  scriptFilename: String
) {
  def run = {
    var scriptLines = Source.fromFile(scriptFilename).getLines
    scriptLines = scriptLines.dropWhile { _.startsWith("#") }
    val script = scriptLines.mkString("\n")
    val eval = new Eval
    eval(script)
    // strip the #
    ""
  }
}

object Sss {
  def main(args: Array[String]) {
    val scriptFilename = args(0)
    new Sss(scriptFilename).run    
  }
}