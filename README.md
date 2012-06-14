sss
===
sss is simple scripting for Scala.  It lets you write simple standalone scripts in Scala
to be executed at the shell prompt.  The scripts can incorporate Maven dependencies using a
simple syntax.  Right now there are only a couple main features:

* Include other script files
* Declare dependencies on Maven artifacts from within your script

Many more features are planned.

Example
=======

apples.sss:

```scala
#!/home/youruser/bin/sss
@depend("org.rogach" %% "scallop" % "0.3.9")
@depend("net.databinder" %% "dispatch-http" % "0.4.1")
!#

import org.rogach.scallop._

object Conf extends ScallopConf(args) {
  val apples = opt[Int]("apples")
}

println("You have " + Conf.apples + " apples")
```

You can then execute helloworld.sss by making it executable and entering ./helloworld.sss

```
$ ./apples.sss --apples 5
You have 5 apples
```

at the .  The dependencies will be downloaded as necessary, the script code will be wrapped in an App
object, compiled and executed.  Full usage here.

Quick Install
=============

Download sss-launch.jar and place it in ```~/bin```.

Create a script to run the jar by entering the following in ~/bin/sss:

java -Xmx1024M -jar `dirname $0`/sss-launch.jar "$@"

Make the script executable using chmod a+x ~/bin/sss.


Notes
=====

The implementation of sss is inspired by and borrows both ideas and code from sbt and twitter util-eval.  It also uses Ivy.  There are other attempts to create a more shell-script-friendly Scala environment.
This is yet another one.  See DESIGN for more thoughts.
