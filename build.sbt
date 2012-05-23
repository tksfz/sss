name := "sss"

version := "0.1"

scalaVersion := "2.9.1"

resolvers += "Twitter" at "http://maven.twttr.com"

libraryDependencies += "com.twitter" %% "util-eval" % "4.0.1" withSources()

libraryDependencies += "org.apache.ivy" % "ivy" % "2.3.0-rc1" withSources()
