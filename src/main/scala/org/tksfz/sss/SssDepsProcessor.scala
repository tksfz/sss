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
import scala.util.matching.Regex

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
  
  def getAllSssFileContents: List[(File, String)] = {
    sssfiles map { sssfile => (sssfile.file, sssfile.contents) } list
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
    rootContents = rootContents.copy(
        contents = getRootImports(tailClassNames) + "\n" + rootContents.contents,
        startingLineOffset = rootContents.startingLineOffset - tailClassNames.size)
    new AllSssWithDeps(nel(rootContents, filesToContents.values.toList.tail))
  }

  def getRootImports(classNames: Iterable[String]) = {
    (classNames map { "import " + _ + "._" }) mkString "\n"
  }
}

object SssDepsProcessor {
  val HEADER_DIRECTIVE_PREFIX = "@"
  val DEPEND_DIRECTIVE = HEADER_DIRECTIVE_PREFIX + "require"
  val INCLUDE_DIRECTIVE = HEADER_DIRECTIVE_PREFIX + "require"
  val ALL_DIRECTIVES = Set(DEPEND_DIRECTIVE, INCLUDE_DIRECTIVE)
}
/**
 * Processes a sss file to extract dependencies
 */
class SssDepsProcessor {
  import SssDepsProcessor._
  
  private val jvmId = java.lang.Math.abs(new Random().nextInt())

  def process(sssfile: File, isRoot: Boolean): ContentsWithDeps = {
    val scriptLines1 = Source.fromFile(sssfile).getLines.toList
    var (shebangLength, scriptLines) = stripFirstShebang(scriptLines1)
    
    val (headerLength, modules, sssfiles) = extractDepends(scriptLines)
    var script = scriptLines drop(headerLength) mkString "\n"
    val className = getClassName(sssfile, script)
    script = wrapScript(className, script, isRoot)
    ContentsWithDeps(sssfile, script, headerLength + shebangLength, className, modules, sssfiles)
  }
  
  // @return pair of (headerLength, stripped body)
  private def stripFirstShebang(lines: List[String]) = {
    if (!lines.isEmpty && lines(0).startsWith("#!")) {
      (1, lines.tail)
    } else {
      (0, lines)
    }
  }
  
  private def extractDepends(script: Iterable[String]): (Int, List[ModuleID], List[File]) = {
    val headerLines = script takeWhile { isValidHeaderLine(_) } toList
    val MvnArtifactRegex = (DEPEND_DIRECTIVE + """\s*\(\s*"([^"]*)"\s*%\s*"([^"]*)"\s*%\s*"([^"]*)"\s*\)""").r
    val SbtArtifactRegex = (DEPEND_DIRECTIVE + """\s*\(\s*"([^"]*)"\s*%%\s*"([^"]*)"\s*%\s*"([^"]*)"\s*\)""").r
    val SssFileRegex = (INCLUDE_DIRECTIVE + """\s*\(\s*"([^"]*)"\s*\)""").r
    val modules = ListBuffer[ModuleID]()
    val sssfiles = ListBuffer[File]()
    for(dependLine <- headerLines) {
      dependLine match {
        case "" => 
        case MvnArtifactRegex(groupId, artifactId, revision) =>
          modules += ModuleID(groupId, artifactId, revision)
        case SbtArtifactRegex(groupId, artifactId, revision) =>
          modules += ModuleID(groupId, mkScalaArtifactId(artifactId), revision)
        case SssFileRegex(filename) =>
          sssfiles += new File(filename)
      }
    }
    (headerLines.size, modules toList, sssfiles toList)
  }
  
  private def isValidHeaderLine(line: String) = { (ALL_DIRECTIVES exists { line.trim.startsWith _ }) || line.trim.isEmpty } 
  
  private def wrapScript(className: String, script: String, isRoot: Boolean): String = {
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
    regexSub(baseName, Eval.classCleaner) { m =>
      "$%02x".format(m.group(0).charAt(0).toInt)
    }
  }
  
  /** Copied from com.twitter.conversions.string in twitter-util */
  def regexSub(wrapped: String, re: Regex)(replace: (Regex.MatchData => String)): String = {
    var offset = 0
    val out = new StringBuilder()
   
    for (m <- re.findAllIn(wrapped).matchData) {
      if (m.start > offset) {
        out.append(wrapped.substring(offset, m.start))
      }
    
      out.append(replace(m))
      offset = m.end
    }
    
    if (offset < wrapped.length) {
      out.append(wrapped.substring(offset))
    }
    out.toString
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
    /** how many lines come before contents in the real source file */
    startingLineOffset: Int,
    className: String,
    modules: List[ModuleID],
    sssfiles: List[File]
) extends PreprocessorResult
