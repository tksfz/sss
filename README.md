[Usage]: https://github.com/tksfz/sss/wiki/usage
[Download]: https://github.com/downloads/tksfz/sss/sss-launch.jar
[Mailing List]: https://groups.google.com/forum/#!forum/sss-scala

# sss

sss is simple scripting for Scala _with dependencies_.  It lets you write simple scripts in Scala
that can be executed at the shell prompt.  The scripts can declare and import Maven dependencies using a
simple ```@require(dependency)``` syntax.  In this early version, there are only a couple main features:

* Include other script and Scala source files
* Declare dependencies on Maven artifacts from within your script

Many more features are planned including the Scala REPL with dependencies, IDE project generation, etc.

## Example

apples.sss:

```scala
#!/home/user/bin/sss
@require("org.rogach" %% "scallop" % "0.3.9")
@require("net.databinder" %% "dispatch-http" % "0.8.8")

import org.rogach.scallop._

object Conf extends ScallopConf(args) {
  val apples = opt[Int]("apples")
}

println("You have " + Conf.apples() + " apples")
```

You can then execute helloworld.sss directly from your shell prompt:

```
$ ./apples.sss --apples 5
You have 5 apples
```

When your script runs, any dependencies will be downloaded as necessary, then the script code will be wrapped,
compiled and executed.  Full [Usage] here.

## Quick Install

Download [sss-launch.jar][Download] and place it in ```~/bin```.

Create a script to run the jar by entering the following in ~/bin/sss:

```
#!/bin/sh
java -Xmx1024M -jar `dirname $0`/sss-launch.jar "$@"
```

Make the script executable using ```chmod a+x ~/bin/sss```.


## Notes

The implementation of sss uses code from twitter util-eval and sbt.  It does not import them as libraries in order
to help keep the launcher as small as possible.  Sbt and Scala itself have the ability to run programs as scripts.
This project provides a different feature set and interface.

## Contact

Join the [Mailing List] to discuss the project.  Please send feature requests, ideas, and bug reports to sss at-sign tksfz.org.
