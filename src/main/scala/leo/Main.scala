package leo

import leo.modules._
import leo.modules.Utility._
import leo.modules.output._

/**
 * Entry Point for Leo-III as an executable to
 * proof a TPTP File
 *
 * @author Max Wisniewski
 * @since 7/8/14
 */
object Main {
  private var hook: scala.sys.ShutdownHookThread = _

  /**
   *
   * Tries to proof a Given TPTP file in
   * a given Time.
   *
   * @param args - See [[Configuration]] for argument treatment
   */
  def main(args : Array[String]){
    try {
      val beginTime = System.currentTimeMillis()
      hook = sys.addShutdownHook({
        Out.output(SZSOutput(SZS_Forced, Configuration.PROBLEMFILE, "This one is on you, buddy."))
      })
      try {
        Configuration.init(new CLParameterParser(args))
      } catch {
        case e: IllegalArgumentException => {
          Out.severe(e.getMessage)
          Configuration.help()
          return
        }
      }
      if (Configuration.HELP) {
        Configuration.help()
        return
      }

      val timeout = if (Configuration.TIMEOUT == 0) Double.PositiveInfinity else Configuration.TIMEOUT
      val config = {
        val sb = new StringBuilder()
        sb.append(s"problem(${Configuration.PROBLEMFILE}),")
        sb.append(s"time(${Configuration.TIMEOUT}),")
        sb.append(s"proofObject(${Configuration.PROOF_OBJECT}),")
        sb.append(s"sos(${Configuration.SOS}),")
        // TBA ...
        sb.init.toString()
      }
      Out.config(s"Configuration: $config")

      if (Configuration.isSet("seq")) {
        leo.modules.seqpproc.SeqPProc(beginTime)
      } else if (Configuration.isSet("pure-ext")) {
        RunExternalProver.runExternal()
      } else {
        throw new SZSException(SZS_UsageError, "standard mode not included right now, use --seq")
      }
      
    } catch {
      case e:SZSException => {
        Out.comment("Hex: OUT OF CHEESE ERROR +++ MELON MELON MELON +++ REDO FROM START")
        Out.output(SZSOutput(e.status, Configuration.PROBLEMFILE,e.toString))
        Out.debug(e.debugMessage)
        Out.trace(stackTraceAsString(e))
        if (e.getCause != null) {
          Out.trace("Caused by: " + e.getCause.getMessage)
          Out.trace("at: " + e.getCause.getStackTrace.toString)
        }
      }
      case e:Throwable => {
        Out.comment("Hex: OUT OF CHEESE ERROR +++ MELON MELON MELON +++ REDO FROM START")
        Out.output(SZSOutput(SZS_Error, Configuration.PROBLEMFILE,e.toString))
        Out.trace(stackTraceAsString(e))
        if (e.getCause != null) {
          Out.trace("Caused by: " + e.getCause.getMessage)
          Out.trace("at: " + e.getCause.getStackTrace.toString)
        }
      }
    } finally {
      hook.remove() // When we reach this code we didnt face a SIGTERM etc. so remove the hook.
    }
  }
}
