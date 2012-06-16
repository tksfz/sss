package org.tksfz.sss

import java.io.File
import scala.io.Source
import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DefaultDependencyArtifactDescriptor
import org.apache.ivy.core.retrieve.RetrieveOptions
import org.apache.ivy.core.retrieve.RetrieveReport
import org.apache.ivy.core.report.ResolveReport
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.util.DefaultMessageLogger
import org.apache.ivy.util.Message

class Sss(
    scriptFilename: String) {
  
  def run(args: Array[String]) {
    val alldeps = AllSssWithDeps(new File(scriptFilename))
    
    val rr = ivy(alldeps.getAllModules)
    val libs: List[String] = rr.getAllArtifactsReports map { _.getLocalFile.getPath } toList 
    val eval = new Eval(libs)
    val ppfs = (alldeps.sssfiles map { sssfile => new PreprocessedFile {
      override val file = sssfile.file
      override val contents = sssfile.contents
      override val startingLineOffset = sssfile.startingLineOffset }
    }).list
    println(ppfs map { ppf => (ppf.file, ppf.startingLineOffset) })
    val cls2 = eval.compileAndGet(ppfs, alldeps.getRootClassName, true)
    runAppClass(cls2, args)
  }
  
  private def runAppClass(clazz: Class[_], args: Array[String]) = clazz.getConstructor().newInstance().asInstanceOf[App].main(args)
  
  def ivy(depends: Traversable[ModuleID]) = {
    val ivy = Ivy.newInstance
    ivy.getLoggerEngine.pushLogger(new DefaultMessageLogger(Message.MSG_ERR))
    ivy.configureDefault
    val md = DefaultModuleDescriptor.newDefaultInstance(ModuleRevisionId.newInstance("org.tksfz", "somescript", "0.1"))
    val dds = toDDD(depends)
    addDeps(md, dds)
    val ro = new ResolveOptions
    val resolveReport = ivy.resolve(md, ro)
    //val rr = ivy.retrieve(md.getModuleRevisionId, "lib/[conf]/[artifact].[ext]", new RetrieveOptions)
    resolveReport
  }
  
  def toDDD(depends: Traversable[ModuleID]) = {
    depends map { depend =>
      val mrid = ModuleRevisionId.newInstance(depend.groupId, depend.artifactId, depend.revision)
      val ddd = new DefaultDependencyDescriptor(mrid, true)
      ddd.addDependencyConfiguration(ModuleDescriptor.DEFAULT_CONFIGURATION, ModuleDescriptor.DEFAULT_CONFIGURATION)
      ddd
    }
  }
  
  def addDeps(md: DefaultModuleDescriptor, dds: Traversable[DependencyDescriptor]) = {
    for(dd <- dds) {
      md.addDependency(dd)
    }
  } 
  
}

object Sss {
  def main(args: Array[String]) {
    val scriptFilename = args(0)
    new Sss(scriptFilename).run(args.tail)    
  }
}