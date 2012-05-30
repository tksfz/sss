package org.tksfz.sss

import java.io.File

import scala.collection.mutable.ListBuffer
import scala.collection.Iterator
import scala.collection.Map
import scala.collection.mutable
import scala.io.Source

/**
 * AllSssWithDeps is the result of a recursive evaluation of all the dependencies of a
 * given root sss file.  It is the collection of sss files connected by @depends
 * relationships that will be compiled together.  Along with their module (external)
 * dependencies.
 * 
 * AllSssWithDeps is like a sss "project"
 */
class AllSssWithDeps(
  val sssfiles: List[(File, SssWithDeps)]
) {
  
  def getAllSssFiles: List[File] = {
    sssfiles map { _._1 }
  }
  
  def getAllModules: List[ModuleID] = {
    sssfiles flatMap { _._2.modules }
  }
  
  override def toString = sssfiles.toString
}

object AllSssWithDeps {
  def apply(rootfile: File) = {
    var unprocessedFiles = mutable.HashSet(rootfile)
    var map: Map[File, SssWithDeps] = new mutable.HashMap
    val proc = new DepsProcessor
    
    while(!unprocessedFiles.isEmpty) {
      val newUnprocessedFiles = new mutable.HashSet[File]
      for(file <- unprocessedFiles) {
        val deps = proc.process(file)
        map += (file -> deps)
        for(newfile <- deps.sssfiles) {
          if (!map.contains(newfile) && !unprocessedFiles.contains(newfile) && !newUnprocessedFiles.contains(newfile)) {
            newUnprocessedFiles += newfile
          }
        }
      }
      unprocessedFiles = newUnprocessedFiles
    }
    new AllSssWithDeps(map toList)
  }
}

/**
 * Processes a sss file to extract dependencies
 */
class DepsProcessor {
  def process(sssfile: File): SssWithDeps = {
    var scriptLines = Source.fromFile(sssfile).getLines
    // strip the #
    scriptLines = scriptLines dropWhile { _ startsWith "#" }
    
    // return pair with list and new script
    val depends = extractDepends(scriptLines)
    depends
  }
  
  def extractDepends(script: Iterator[String]): SssWithDeps = {
    val dependLines = script takeWhile { _ startsWith "@depends" }
    val mvnArtifactRegex = """@depends\s*\(\s*"([^"]*)"\s*%\s*"([^"]*)"\s*%\s*"([^"]*)"\s*\)""".r
    val sbtArtifactRegex = """@depends\s*\(\s*"([^"]*)"\s*%%\s*"([^"]*)"\s*%\s*"([^"]*)"\s*\)""".r
    val sssFileRegex = """@depends\s*\(\s*"([^"]*)"\s*\)""".r
    val modules = ListBuffer[ModuleID]()
    val sssfiles = ListBuffer[File]()
    for(dependLine <- dependLines) {
        dependLine match {
          case mvnArtifactRegex(groupId, artifactId, revision) =>
            modules += ModuleID(groupId, artifactId, revision)
          case sbtArtifactRegex(groupId, artifactId, revision) =>
            modules += ModuleID(groupId, mkScalaArtifactId(artifactId), revision)
          case sssFileRegex(filename) =>
            sssfiles += new File(filename)
        }
    }
    val newscript = script dropWhile { _ startsWith "@depends" }
    new SssWithDeps(newscript, modules toList, sssfiles toList)
  }
  
  def mkScalaArtifactId(artifactId: String) = {
    artifactId + "_" + getScalaVersion.get
  }
 
  def getScalaVersion = scala.tools.nsc.Properties.releaseVersion

}

trait PreprocessorResult {
  val newcontents: Iterator[String]
}

case class SssWithDeps(
  override val newcontents: Iterator[String],
  val modules: List[ModuleID],
  val sssfiles: List[File]
) extends PreprocessorResult {
}
