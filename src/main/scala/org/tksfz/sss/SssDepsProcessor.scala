package org.tksfz.sss

import java.io.File
import scala.collection.mutable.ListBuffer
import scala.collection.Iterator
import scala.collection.Map
import scala.collection.mutable
import scala.io.Source
import java.security.MessageDigest
import java.math.BigInteger
import java.util.Random
import scalaz.NonEmptyList
import NonEmptyList._

/**
 * AllSssWithDeps is the result of a recursive evaluation of all the dependencies of a
 * given root sss file.  It is the collection of sss files connected by @depends
 * relationships that will be compiled together.  Along with their module (external)
 * dependencies.
 * 
 * AllSssWithDeps is like a sss "project"
 */
class AllSssWithDeps(
    val sssfiles: NonEmptyList[ContentsWithDeps]
) {
  
  def getAllSssFiles: NonEmptyList[File] = {
    sssfiles map { _.file }
  }
  
  def getAllModules: List[ModuleID] = {
    sssfiles.list.flatMap { _.modules }
  }
  
  def getRootClassName = sssfiles.head.className
  
  def getAllSssFileContents: NonEmptyList[(File, String)] = {
    sssfiles map { sssfile => (sssfile.file, sssfile.contents) }
  }
  
  override def toString = sssfiles.toString
}

/**
 * Processing is initiated at some starting point.  This is the main script being executed and
 * is typically an sss file.  This starting point is called the "root" or the "application" file.
 * 
 * The root file can depend on other resources, and these resources may depend on other resources.
 * This forms a tree (or really a graph, a DAG, since there can be inter-dependencies).  Call this
 * graph the project.
 * 
 * There are a few different kinds of resources:
 * - .scala files
 * - .sss files
 * - .jar files
 * - classes dirs?
 * - maven/sbt artifact id's
 * 
 * Of these, only sss files are allowed to declare further dependencies.  All the others, including
 * .scala files are thus terminal/leaf nodes.
 * 
 * The node class declares these dependencies in multiple lists (rather than, say, a single
 * heterogenous list of some polymorphic element type).
 *  
 */
object AllSssWithDeps {
  def apply(rootfile: File) = {
    var unprocessedFiles = mutable.HashSet(rootfile)
    var filesToContents: Map[File, ContentsWithDeps] = new mutable.LinkedHashMap
    val proc = new SssDepsProcessor
    
    var isRoot = true
    while(!unprocessedFiles.isEmpty) {
      val newUnprocessedFiles = new mutable.HashSet[File]
      for(file <- unprocessedFiles) {
        val deps = proc.process(file, isRoot)
        isRoot = false
        filesToContents += (file -> deps)
        for(newfile <- deps.sssfiles) {
          if (!filesToContents.contains(newfile) && !unprocessedFiles.contains(newfile) && !newUnprocessedFiles.contains(newfile)) {
            newUnprocessedFiles += newfile
          }
        }
      }
      unprocessedFiles = newUnprocessedFiles
    }
    val tailClassNames = filesToContents.values.tail map { _.className }
    var rootContents = filesToContents(rootfile)
    rootContents = rootContents.copy(contents = getRootImports(tailClassNames) + "\n" + rootContents.contents)
    new AllSssWithDeps(nel(rootContents, filesToContents.values.toList.tail))
  }

  def getRootImports(classNames: Iterable[String]) = {
    (classNames map { "import " + _ + "._" }) mkString "\n"
  }
}


/**
 * Processes a sss file to extract dependencies
 */
class SssDepsProcessor {
  private val jvmId = java.lang.Math.abs(new Random().nextInt())

  def process(sssfile: File, isRoot: Boolean): ContentsWithDeps = {
    var scriptLines = Source.fromFile(sssfile).getLines
    scriptLines = scriptLines dropWhile { _ startsWith "#" } // strip the #
    
    val (modules, sssfiles) = extractDepends(scriptLines)
    var script = scriptLines mkString "\n"
    val className = getClassName(sssfile, script)
    script = wrapScript(className, script, isRoot)
    ContentsWithDeps(sssfile, script, className, modules, sssfiles)
  }
  
  private def extractDepends(script: Iterator[String]): (List[ModuleID], List[File]) = {
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
    (modules toList, sssfiles toList)
  }
  
  def wrapScript(className: String, script: String, isRoot: Boolean): String = {
    if (isRoot) wrapCodeInApp(className, script) else wrapCodeInObject(className, script)
  }
  
  def mkscript(file: File, script: String) = {
    wrapCodeInObject(getClassName(file, script), script)
  }
  
  def getClassName(file: File, script: String) = {
    val sourceChecksum = uniqueId(script, None)
    val cleanBaseName = fileToClassName(file)
    val className = "Evaluator__%s_%s".format(cleanBaseName, sourceChecksum)
    className
  }

  private[sss] def uniqueId(code: String, idOpt: Option[Int] = Some(jvmId)): String = {
    val digest = MessageDigest.getInstance("SHA-1").digest(code.getBytes())
    val sha = new BigInteger(1, digest).toString(16)
    idOpt match {
      case Some(id) => sha + "_" + jvmId
      case _ => sha
    }
  }

  private[sss] def fileToClassName(f: File): String = {
    // HOPE YOU'RE HAPPY GUYS!!!!
    /*          __
     *    __/|_/ /_  __  ______ ________/|_
     *   |    / __ \/ / / / __ `/ ___/    /
     *  /_ __/ / / / /_/ / /_/ (__  )_ __|
     *   |/ /_/ /_/\__,_/\__, /____/ |/
     *                  /____/
     */
    val fileName = f.getName
    val baseName = fileName.lastIndexOf('.') match {
      case -1 => fileName
      case dot => fileName.substring(0, dot)
    }
    import _root_.com.twitter.conversions.string._
    baseName.regexSub(Eval.classCleaner) { m =>
      "$%02x".format(m.group(0).charAt(0).toInt)
    }
  }

  
  def mkScalaArtifactId(artifactId: String) = {
    artifactId + "_" + getScalaVersion.get
  }
 
  private def getScalaVersion = scala.tools.nsc.Properties.releaseVersion

  private def wrapCodeInObject(className: String, code: String) = {
    "object " + className + " { " + code + "\n}"
  }
  
  private def wrapCodeInApp(className: String, code: String) = {
    "class " + className + " extends App { " + code + "\n}"
  }
  
}

trait PreprocessorResult {
  val file: File
  val contents: String
}

case class ContentsWithDeps(
    override val file: File,
    override val contents: String,
    className: String,
    modules: List[ModuleID],
    sssfiles: List[File]
) extends PreprocessorResult
