package leo.modules.agent.preprocessing

import leo.agents.{AbstractAgent, Agent, Task}
import leo.datastructures.ClauseAnnotation.InferredFrom
import leo.datastructures.{AnnotatedClause, Clause, ClauseAnnotation, ClauseProxy}
import leo.datastructures.blackboard._
import leo.datastructures.context.Context
import leo.modules.calculus.CalculusRule
import leo.modules.preprocessing._

/**
  * Created by mwisnie on 3/7/16.
  */
class NormalizationAgent(cs : Context*) extends AbstractAgent {
  override def name: String = "normalization_agent"
  override val after : Set[Agent] = Set(EqualityReplaceAgent)
  val norms : Seq[Normalization] = Seq(Simplification, DefExpSimp, NegationNormal, Skolemization, PrenexNormal) // TODO variable?

  override def filter(event: Event): Iterable[Task] = event match {
    case DataEvent(cl : ClauseProxy, ClauseType) => commonFilter(cl, Context())
    case DataEvent((cl : ClauseProxy, c : Context), ClauseType) => commonFilter(cl, c)
    case _ => Seq()
  }

  private def commonFilter(cl : ClauseProxy, c : Context) : Iterable[Task] = {
    var openNorm : Seq[Normalization] = norms
    val toInsertContext = ((if(cs exists (ce => Context.isAncestor(ce)(c))) Seq(c) else Seq()) ++ (cs filter Context.isAncestor(c))).toSet
    var clause = cl.cl
    while(openNorm.nonEmpty && cl.cl == clause){
      val norm = openNorm.head
      openNorm = openNorm.tail
      clause = norm(clause)
    }
    if(cl.cl == clause)
      Seq()
    else
      toInsertContext map (ci => new NormalizationTask(cl, clause, openNorm, ci, this))
  }
}

class NormalizationTask(cl : ClauseProxy, nc : Clause, openNorm : Seq[Normalization], c : Context, a : Agent) extends Task{
  override def name: String = "normalization_task"
  override def getAgent: Agent = a
  override def writeSet(): Map[DataType, Set[Any]] = Map(ClauseType -> Set(cl))
  override def readSet(): Map[DataType, Set[Any]] = Map()
  override def run: Result = {
    val clause = openNorm.foldRight(nc){(norm, c) => norm(c)}
    val cp = AnnotatedClause(clause, cl.role, InferredFrom(NormalizationRule, cl), ClauseAnnotation.PropNoProp)
    Result().update(ClauseType)((cl, c))((cp, c))
  }
  override def bid: Double = 0.1

  override val pretty: String = s"normalization_task(${cl.cl.pretty})"
  override val toString : String = pretty
}

object NormalizationRule extends CalculusRule {
  override val name: String = "normalization"
}
