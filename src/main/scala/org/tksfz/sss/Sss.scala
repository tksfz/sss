package org.tksfz.sss

import com.twitter.util.Eval
import java.io.File

class Sss(
  scriptFilename: String
) {
  def run = {
    val eval = new Eval
    eval(new File(scriptFilename))
    // strip the #
  }
}

object Sss {
  def main(args: Array[String]) = {
    val scriptFilename = args(0)
  }
}