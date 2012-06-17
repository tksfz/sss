[Usage]: https://github.com/tksfz/sss/wiki/usage

sss
===
sss is simple scripting for Scala _with dependencies_.  It lets you write simple scripts in Scala
that can be executed at the shell prompt.  The scripts can declare and import Maven dependencies using a
simple ```@require(dependency)``` syntax.  In this early version, there are only a couple main features:

* Include other script and Scala source files
* Declare dependencies on Maven artifacts from within your script

Many more features are planned including the Scala REPL with dependencies, IDE project generation, etc.

Example
=======

apples.sss:

```scala
#!/home/user/bin/sss
@depend("org.rogach" %% "scallop" % "0.3.9")
@depend("net.databinder" %% "dispatch-http" % "0.4.1")

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

Quick Install
=============

Download sss-launch.jar and place it in ```~/bin```.

Create a script to run the jar by entering the following in ~/bin/sss:

```
java -Xmx1024M -jar `dirname $0`/sss-launch.jar "$@"
```

Make the script executable using ```chmod a+x ~/bin/sss```.


Notes
=====

The implementation of sss uses code from twitter util-eval and sbt.  It does not import them as libraries in order
to help keep the launcher as small as possible.
