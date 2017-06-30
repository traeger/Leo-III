package leo.modules.external

import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.PosixFilePermissions

import scala.io.{BufferedSource, Codec}
import leo.Configuration
import leo.datastructures._

/**
  * Object to construct provers from their paths.
  * Checks for the commands, for their executability.
  */
object ExternalProver {

  /**
    * Additional time added to the timeout to wait for termination
    */
  final val WAITFORTERMINATION = 1
  final val SCRIPTDIR_NAME: String = "ext_scripts"
  final lazy val SCRIPTDIR: Path = Configuration.LEODIR.resolve(SCRIPTDIR_NAME)
  final lazy val LIMITEDRUN: Path = SCRIPTDIR.resolve("TreeLimitedRun")

  def cleanup(): Unit = {
    if (Files.exists(LIMITEDRUN)) LIMITEDRUN.toFile.delete()
    val cvc4RunScript = SCRIPTDIR.resolve(CVC4.executeScriptName)
    if (Files.exists(cvc4RunScript)) cvc4RunScript.toFile.delete()
    if (Files.exists(SCRIPTDIR)) SCRIPTDIR.toFile.delete()
  }
  /**
    * Creates a prover `name` with an executable at the path `path`.
    * Throws an [[NoSuchMethodException]] if this prover cannot be executed.
    *
    * @param name name of the prover
    * @param path path of the prover
    * @return an abstracted prover `name`
    */
  @throws[NoSuchMethodException]
  def createProver(name : String, path : String) : TptpProver[AnnotatedClause] = {
    createTreeLimitedRunScript()
    name match {
      case "leo2" => createLeo2(path)
      case "nitpick" => createNitpickProver(path)
      case "cvc4" => createCVC4(path)
      case "alt-ergo" => createAltErgo(path)
      case _ => throw new NoSuchMethodException(s"$name not supported by Leo-III. Valid values are: leo2,nitpick,cvc4,alt-ergo")
    }
  }
  private final def createTreeLimitedRunScript(): Unit = {
    if (!Files.exists(SCRIPTDIR)) Files.createDirectory(SCRIPTDIR)
    assert(Files.exists(SCRIPTDIR), "SCRIPTDIR not created")
    if (!Files.exists(LIMITEDRUN)) {
      val filePermissionAttribute = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr--"))
      val file = Files.createFile(LIMITEDRUN, filePermissionAttribute)
      val limitedRunScript = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/scripts/TreeLimitedRun"))(Codec.ISO8859)
      Files.write(file, limitedRunScript.mkString.getBytes(StandardCharsets.ISO_8859_1))
      file.toFile.setExecutable(true)
      assert(Files.exists(LIMITEDRUN), "LIMITEDRUN not created")
    }

    val files = SCRIPTDIR.toFile.listFiles()
    if (files != null) {
      for (f <- files) {
        println(f.toString)
      }
    }
  }
  private final def serviceToPath(cmd : String) : Path = {
    import scala.sys.process._
    val p = Paths.get(cmd)
    if(Files.exists(p) && Files.isExecutable(p)){
      p
    } else {
      val redirectStdErrLogger = ProcessLogger(line => ())
      val which0 = Seq("which", cmd) lineStream_! redirectStdErrLogger
      val which = which0.headOption
      if (which.isDefined) {
        val p2 = Paths.get(which.get)
        if(Files.exists(p2) && Files.isExecutable(p2)){
          p2
        } else {
          throw new NoSuchMethodException(s"'$cmd' is not executable or does not exist.")
        }
      } else {
        throw new NoSuchMethodException(s"'$cmd' is not executable or does not exist.")
      }
    }
  }

  /**
    * Creates LEO-II with the executable `path`.
    * Throws an [[NoSuchMethodException]] if this prover cannot be executed.
    *
    * @param path path of the prover
    * @return an abstracted instance of LEO-II
    */
  @throws[NoSuchMethodException]
  def createLeo2(path : String) : Leo2Prover = {
    val p = if(path == "") serviceToPath("leo") else serviceToPath(path)
    val convert = p.toAbsolutePath.toString
    leo.Out.debug(s"Created Leo2 prover with path '$convert'")
    new Leo2Prover(convert)
  }

  /**
    * Creates a callable instance of Nitpick.
    * The given `path` has to reference the isabelle system.
    *
    * Throws an [[NoSuchMethodException]] if this prover cannot be executed.
    *
    * @param path path of the prover
    * @return an abstracted instance of Nitpick
    */
  @throws[NoSuchMethodException]
  def createNitpickProver(path : String) : NitpickProver = {
    val p = if(path == "") serviceToPath("isabelle") else serviceToPath(path)
    val convert = p.toAbsolutePath.toString
    leo.Out.debug(s"Created Nitpick prover with path '$convert' (Isabelle)")
    new NitpickProver(convert)
  }

  final def createCVC4(path : String) : CVC4 = {
    createCVC4RunScript()
    val p = if(path == "") serviceToPath("cvc4") else serviceToPath(path)
    val convert = p.toAbsolutePath.toString
    leo.Out.debug(s"Created CVC4 prover with path '$convert'")
    CVC4(SCRIPTDIR.resolve(CVC4.executeScriptName).toString, convert)
  }
  private final def createCVC4RunScript(): Unit = {
    val cvc4RunPath = SCRIPTDIR.resolve(CVC4.executeScriptName)
    if (!Files.exists(cvc4RunPath)) {
      val filePermissionAttribute = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr--"))
      val file = Files.createFile(cvc4RunPath, filePermissionAttribute)
      Files.write(file, CVC4.executeScript.mkString.getBytes(StandardCharsets.UTF_8))
      file.toFile.setExecutable(true)
    }
  }

  final def createAltErgo(path: String): AltErgo = {
    val p = if(path == "") serviceToPath("alt-ergo") else serviceToPath(path)
    val convert = p.toAbsolutePath.toString
    val proc = Command("which why3").exec()
    proc.waitFor()
    if (proc.exitValue != 0) throw new NoSuchMethodException("Why3 executable not found in path. why3 is necessary to run AltErgo. Please add why3 to PATH and retry.")
    else {
      val proc = Command("why3 --list-provers | grep Alt-Ergo").exec()
      proc.waitFor()
      if (proc.exitValue != 0) {
        val proc = Command(s"why3 config --add-prover alt-ergo $convert").exec()
        proc.waitFor()
        if (proc.exitValue != 0) throw new NoSuchMethodException("Registration of AltErgo in why3 not successful. Check debug logs.")
      }
      leo.Out.debug(s"Create AltErgo prover with path '$convert'")
      AltErgo(convert)
    }
  }

  final def limitedRun(timeout: Int, args: Seq[String]): Seq[String] = {
    Seq(LIMITEDRUN.toString, String.valueOf(timeout), String.valueOf(timeout)) ++ args
  }
}


class CVC4(execScript: String, val path: String) extends TptpProver[AnnotatedClause] {
  final val name: String = "cvc4"
  final val capabilities: Capabilities.Info = Capabilities(Capabilities.TFF -> Seq())

  protected[external] def constructCall(args: Seq[String], timeout: Int,
                                        problemFileName: String): Seq[String] = {
    ExternalProver.limitedRun(2*timeout+2, Seq(execScript, path, problemFileName))
  }
}
object CVC4 {
  @inline final def apply(execScript: String, path: String): CVC4 = new CVC4(execScript, path)
  final val executeScriptName: String = "run-script-cascj8-tfa"
  final def executeScript: BufferedSource = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/scripts/" + executeScriptName))
}

class AltErgo(val path: String) extends TptpProver[AnnotatedClause] {
  final val name: String = "AltErgo"
  final val capabilities: Capabilities.Info = Capabilities(Capabilities.TFF -> Seq(Capabilities.Polymorphism))

  protected[external] def constructCall(args: Seq[String], timeout: Int,
                                        problemFileName: String): Seq[String] = {
    ExternalProver.limitedRun(timeout+2, Seq("why3", "prove", "-F", "tptp", "-t", String.valueOf(timeout), "-P", "Alt-Ergo", problemFileName))
  }
}
object AltErgo {
  @inline final def apply(path: String): AltErgo = new AltErgo(path)
}



class Leo2Prover(val path : String) extends TptpProver[AnnotatedClause] {
  override val name: String = "leo2"

  final val capabilities: Capabilities.Info = Capabilities(Capabilities.THF -> Seq())

  override protected[external] def constructCall(args: Seq[String], timeout: Int, problemFileName: String): Seq[String] = {
    val timeout0 = if (timeout < 60) 60 else timeout
    ExternalProver.limitedRun(timeout, Seq(path, "-t", timeout0.toString) ++ args ++ Seq(problemFileName))
  }
}


class NitpickProver(val path : String) extends TptpProver[AnnotatedClause] {
  override def name: String = "nitpick"

  final val capabilities: Capabilities.Info = Capabilities(Capabilities.THF -> Seq())

  override protected def constructCall(args: Seq[String], timeout: Int, problemFileName: String): Seq[String] = {
    ExternalProver.limitedRun(timeout, Seq(path, "tptp_nitpick", timeout.toString) ++ Seq(problemFileName))
  }
}
