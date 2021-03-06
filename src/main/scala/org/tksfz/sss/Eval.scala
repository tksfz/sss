package org.tksfz.sss

/*
 * Copyright 2010 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io._
import java.math.BigInteger
import java.net.URLClassLoader
import java.security.MessageDigest
import java.util.Random
import java.util.jar.JarFile
import scala.collection.mutable
import scala.io.Source
import scala.tools.nsc.{Global, Settings}
import scala.tools.nsc.interpreter.AbstractFileClassLoader
import scala.tools.nsc.io.{AbstractFile, VirtualDirectory}
import scala.tools.nsc.reporters.AbstractReporter
import scala.tools.nsc.util.{BatchSourceFile, Position}
import scala.util.matching.Regex
import java.net.URL
import scala.tools.nsc.io.VirtualFile
import scala.tools.nsc.util.ScriptSourceFile
import scala.tools.nsc.util.SourceFile

/**
 * Evaluate a file or string and return the result.
 */
@deprecated("use a throw-away instance of Eval instead")
object Eval extends Eval {
  private val jvmId = java.lang.Math.abs(new Random().nextInt())
  val classCleaner: Regex = "\\W".r
}

trait PreprocessedFile {
  val file: File
  val contents: String
  val startingLineOffset: Int
}

/**
 * Evaluates files, strings, or input streams as Scala code, and returns the result.
 *
 * If `target` is `None`, the results are compiled to memory (and are therefore ephemeral). If
 * `target` is `Some(path)`, the path must point to a directory, and classes will be saved into
 * that directory.
 *
 * Eval also supports a limited set of preprocessors. Currently, "limited" means "exactly one":
 * directives of the form `#include <file>`.
 *
 * The flow of evaluation is:
 * - extract a string of code from the file, string, or input stream
 * - run preprocessors on that string
 * - wrap processed code in an `apply` method in a generated class
 * - compile the class
 * - contruct an instance of that class
 * - return the result of `apply()`
 */
class Eval(
    target: Option[File],
    dependPath: List[String]) {
  /**
   * empty constructor for backwards compatibility
   */
  def this() {
    this(None, Nil)
  }
  
  def this(dependPath: List[String]) {
    this(None, dependPath)
  }

  import Eval.jvmId

  private lazy val compilerPath = try {
    jarPathOfClass("scala.tools.nsc.Interpreter")
  } catch {
    case e =>
      throw new RuntimeException("Unable lo load scala interpreter from classpath (scala-compiler jar is missing?)", e)
  }

  private lazy val libPath = try {
    jarPathOfClass("scala.ScalaObject")
  } catch {
    case e =>
      throw new RuntimeException("Unable to load scala base object from classpath (scala-library jar is missing?)", e)
  }

  private[this] val STYLE_INDENT = 2
  private[this] lazy val compiler = new StringCompiler(STYLE_INDENT, target)

  /**
   * write the current checksum to a file
   */
  def writeChecksum(checksum: String, file: File) {
    val writer = new FileWriter(file)
    writer.write("%s".format(checksum))
    writer.close
  }

  /**
   * val i: Int = new Eval()("1 + 1") // => 2
   */
  def apply[T](code: String, resetState: Boolean = true): T = {
    val processed = code
    applyProcessed(processed, resetState)
  }

  /**
   * val i: Int = new Eval()(getClass.getResourceAsStream("..."))
   */
  def apply[T](stream: InputStream): T = {
    apply(Source.fromInputStream(stream).mkString)
  }

  /**
   * same as apply[T], but does not run preprocessors.
   * Will generate a classname of the form Evaluater__<unique>,
   * where unique is computed from the jvmID (a random number)
   * and a digest of code
   */
  def applyProcessed[T](code: String, resetState: Boolean): T = {
    val id = uniqueId(code)
    val className = "Evaluator__" + id
    applyProcessed(className, code, resetState)
  }

  /**
   * same as apply[T], but does not run preprocessors.
   */
  def applyProcessed[T](className: String, code: String, resetState: Boolean): T = {
    val cls = compiler(wrapCodeInClass(className, code), className, resetState)
    cls.getConstructor().newInstance().asInstanceOf[() => Any].apply().asInstanceOf[T]
  }
  
  def compileAndGet(ppfs: List[_ <: PreprocessedFile], className: String, resetState: Boolean): Class[_] = {
    val cls = compiler.compileAndGet(ppfs, className, resetState)
    cls
  } 

  /**
   * Compile an entire source file into the virtual classloader.
   */
  def compile(code: String) {
    compiler(code)
  }

  /**
   * Like `Eval()`, but doesn't reset the virtual classloader before evaluating. So if you've
   * loaded classes with `compile`, they can be referenced/imported in code run by `inPlace`.
   */
  def inPlace[T](code: String) = {
    apply[T](code, false)
  }

  /**
   * Check if code is Eval-able.
   * @throws CompilerException if not Eval-able.
   */
  def check(code: String) {
    val id = uniqueId(code)
    val className = "Evaluator__" + id
    val wrappedCode = wrapCodeInClass(className, code)
    compile(wrappedCode) // may throw CompilerException
  }

  /**
   * Check if files are Eval-able.
   * @throws CompilerException if not Eval-able.
   */
  def check(files: File*) {
    val code = files.map { scala.io.Source.fromFile(_).mkString }.mkString("\n")
    check(code)
  }

  /**
   * Check if stream is Eval-able.
   * @throws CompilerException if not Eval-able.
   */
  def check(stream: InputStream) {
    check(scala.io.Source.fromInputStream(stream).mkString)
  }

  def findClass(className: String): Class[_] = {
    compiler.findClass(className).getOrElse { throw new ClassNotFoundException("no such class: " + className) }
  }

  private[sss] def uniqueId(code: String, idOpt: Option[Int] = Some(jvmId)): String = {
    val digest = MessageDigest.getInstance("SHA-1").digest(code.getBytes())
    val sha = new BigInteger(1, digest).toString(16)
    idOpt match {
      case Some(id) => sha + "_" + jvmId
      case _ => sha
    }
  }

  /*
   * Wrap source code in a new class with an apply method.
   */
  private def wrapCodeInClass(className: String, code: String) = {
    "class " + className + " extends (() => Any) {\n" +
    "  def apply() = {\n" +
    code + "\n" +
    "  }\n" +
    "}\n"
  }
  
  /*
   * For a given FQ classname, trick the resource finder into telling us the containing jar.
   */
  private def jarPathOfClass(className: String) = try {
    val resource = className.split('.').mkString("/", "/", ".class")
    val path = getClass.getResource(resource).getPath
    val indexOfFile = path.indexOf("file:") + 5
    val indexOfSeparator = path.lastIndexOf('!')
    List(path.substring(indexOfFile, indexOfSeparator))
  }

  /*
   * Try to guess our app's classpath.
   * This is probably fragile.
   */
  lazy val impliedClassPath: List[String] = {
    val loader = this.getClass.getClassLoader.asInstanceOf[URLClassLoader]
    val currentClassPath = loader.getURLs filter {
      _.getProtocol == "file"
    } map { u =>
      new File(u.toURI).getPath
    } toList

    // if there's just one thing in the classpath, and it's a jar, assume an executable jar.
    currentClassPath ::: (if (currentClassPath.size == 1 && currentClassPath(0).endsWith(".jar")) {
      val jarFile = currentClassPath(0)
      val relativeRoot = new File(jarFile).getParentFile()
      val nestedClassPath = new JarFile(jarFile).getManifest.getMainAttributes.getValue("Class-Path")
      if (nestedClassPath eq null) {
        Nil
      } else {
        nestedClassPath.split(" ").map { f => new File(relativeRoot, f).getAbsolutePath }.toList
      }
    } else {
      Nil
    })
  }
  
  trait OffsetSourceFile {
    this: SourceFile =>
      val startingLineOffset: Int
  }
  
  /**
   * Dynamic scala compiler. Lots of (slow) state is created, so it may be advantageous to keep
   * around one of these and reuse it.
   */
  private class StringCompiler(lineOffset: Int, targetDir: Option[File]) {
    val target = targetDir match {
      case Some(dir) => AbstractFile.getDirectory(dir)
      case None => new VirtualDirectory("(memory)", None)
    }

    val cache = new mutable.HashMap[String, Class[_]]()

    val settings = new Settings
    settings.nowarnings.value = true // warnings are exceptions, so disable
    settings.outputDirs.setSingleOutput(target)

    val pathList = compilerPath ::: libPath
    settings.bootclasspath.value = pathList.mkString(File.pathSeparator)
    settings.classpath.value = (pathList ::: impliedClassPath ::: dependPath).mkString(File.pathSeparator)

    val reporter = new AbstractReporter {
      val settings = StringCompiler.this.settings
      val messages = new mutable.ListBuffer[List[String]]

      def display(pos: Position, message: String, severity: Severity) {
        severity.count += 1
        val severityName = severity match {
          case ERROR   => "error: "
          case WARNING => "warning: "
          case _ => ""
        }
        val startingLineOffset = pos.source match { case osf: OffsetSourceFile => osf.startingLineOffset case _ => lineOffset }
        messages += (severityName + "file " + pos.source + ", line " + (pos.line + startingLineOffset) + ": " + message) ::
          (if (pos.isDefined) {
            pos.inUltimateSource(pos.source).lineContent.stripLineEnd ::
              (" " * (pos.column - 1) + "^") ::
              Nil
          } else {
            Nil
          })
      }

      def displayPrompt {
        // no.
      }

      override def reset {
        super.reset
        messages.clear()
      }
    }

    val global = new Global(settings, reporter)

    /*
     * Class loader for finding classes compiled by this StringCompiler.
     * After each reset, this class loader will not be able to find old compiled classes.
     */
    var dependClassLoader = newURLClassLoader
    var classLoader = new AbstractFileClassLoader(target, this.getClass.getClassLoader)

    def reset() {
      targetDir match {
        case None => {
          target.asInstanceOf[VirtualDirectory].clear
        }
        case Some(t) => {
          target.foreach { abstractFile =>
            if (abstractFile.file == null || abstractFile.file.getName.endsWith(".class")) {
              abstractFile.delete
            }
          }
        }
      }
      cache.clear()
      reporter.reset
      classLoader = new AbstractFileClassLoader(target, dependClassLoader)
    }
    
    def newURLClassLoader() = {
      val urls: Array[URL] = dependPath map { path => new URL("file:" + path) } toArray 
      val cl = new URLClassLoader(urls, this.getClass.getClassLoader)
      cl
    }

    object Debug {
      val enabled =
        System.getProperty("eval.debug") != null

      def printWithLineNumbers(code: String) {
        printf("Code follows (%d bytes)\n", code.length)

        var numLines = 0
        code.lines foreach { line: String =>
          numLines += 1
          println(numLines.toString.padTo(5, ' ') + "| " + line)
        }
      }
    }

    def findClass(className: String): Option[Class[_]] = {
      synchronized {
        cache.get(className).orElse {
          try {
            val cls = classLoader.loadClass(className)
            cache(className) = cls
            Some(cls)
          } catch {
            case e: ClassNotFoundException => None
          }
        }
      }
    }

    /**
     * Compile scala code. It can be found using the above class loader.
     */
    def apply(code: String) {
      if (Debug.enabled)
        Debug.printWithLineNumbers(code)

      // if you're looking for the performance hit, it's 1/2 this line...
      val compiler = new global.Run
      val sourceFiles = List(new BatchSourceFile("(inline)", code))
      // ...and 1/2 this line:
      compiler.compileSources(sourceFiles)

      if (reporter.hasErrors || reporter.WARNING.count > 0) {
        throw new CompilerException(reporter.messages.toList)
      }
    }
    
    def compile(ppfs: List[_ <: PreprocessedFile]) {
      val compiler = new global.Run
      val sourceFiles = ppfs map { ppf =>       
        val vf = new VirtualFile("(inline: " + ppf.file + ")") {
            override def container: AbstractFile = new VirtualFile("whatever")
        }
        new BatchSourceFile(vf, ppf.contents) with OffsetSourceFile {
          override val startingLineOffset = ppf.startingLineOffset
        }
      }
      compiler.compileSources(sourceFiles)
      if (reporter.hasErrors || reporter.WARNING.count > 0) {
        throw new CompilerException(reporter.messages.toList)
      }
    }
    
    /**
     * Compile a new class, load it, and return it. Thread-safe.
     */
    def apply(code: String, className: String, resetState: Boolean = true): Class[_] = {
      synchronized {
        if (resetState) reset()
        findClass(className).getOrElse {
          apply(code)
          findClass(className).get
        }
      }
    }
    
    def compileAndGet(ppfs: List[_ <: PreprocessedFile], className: String, resetState: Boolean): Class[_] = {
      synchronized {
        if (resetState) reset()
        findClass(className).getOrElse {
          compile(ppfs)
          findClass(className).get
        }
      }
    }
  }

  class CompilerException(val messages: List[List[String]]) extends Exception(
    "Compiler exception " + messages.map(_.mkString("\n")).mkString("\n"))
}
