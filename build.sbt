name := "sss"

version := "0.1"

scalaVersion := "2.9.2"

resolvers += "Twitter" at "http://maven.twttr.com"

libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.9.2" withSources()

libraryDependencies += "org.apache.ivy" % "ivy" % "2.3.0-rc1" withSources()

libraryDependencies += "org.scalaz" %% "scalaz-core" % "6.0.4" withSources()
