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

class Sss(
  scriptFilename: String
) {
  
  def run {
    var scriptLines = Source.fromFile(scriptFilename).getLines
    // strip the #
    scriptLines = scriptLines.dropWhile { _.startsWith("#") }
    val script = scriptLines.mkString("\n")
    val eval = new Eval
    eval(script)
    
    ivy
  }
  
  def ivy = {
    val ivy = Ivy.newInstance
    ivy.configureDefault
    val md = DefaultModuleDescriptor.newDefaultInstance(ModuleRevisionId.newInstance("org.tksfz", "somescript", "0.1"))
    val ddd = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org.slf4j", "slf4j-api", "1.6.4"), true)
    ddd.addDependencyConfiguration(ModuleDescriptor.DEFAULT_CONFIGURATION, ModuleDescriptor.DEFAULT_CONFIGURATION)
    //val dad = new DefaultDependencyArtifactDescriptor
    //ddd.addDependencyArtifact(x$1, x$2)
    md.addDependency(ddd)
    val ro = new ResolveOptions
    val resolveReport = ivy.resolve(md, ro)
    println(resolveReport)
    val rr = ivy.retrieve(md.getModuleRevisionId, "lib/[conf]/[artifact].[ext]", new RetrieveOptions)
    println(rr)
  }
}

object Sss {
  def main(args: Array[String]) {
    val scriptFilename = args(0)
    new Sss(scriptFilename).run    
  }
}