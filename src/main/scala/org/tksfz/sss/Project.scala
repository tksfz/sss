package org.tksfz.sss

import java.io.File

/**
 * A Project is a collection of sss files connected by @depends relationships
 * that are compiled together
 */
class Project(
  sssFiles: List[SssFile]
) {
  
}

object Project {
  def apply(rootFile: File): Project = {
    // process files for dependencies recursively
    null
  }
}

class SssFile(
  file: File
) {
  
}