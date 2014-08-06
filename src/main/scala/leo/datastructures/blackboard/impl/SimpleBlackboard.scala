package leo.datastructures.blackboard.impl



import leo.agents.{Task, Agent}
import leo.datastructures.blackboard.scheduler.Scheduler
import leo.datastructures.internal.{ Term => Formula }
import scala.collection.concurrent.TrieMap
import leo.datastructures.blackboard._
import scala.collection.mutable
import scala.collection.mutable.{Queue, Map => MMap}

/**
 * Starting Blackboard. Just to replace @see{leoshell.FormulaHandle}
 *
 * @author Max Wisniewski <max.wisniewski@fu-berlin.de>
 * @author Daniel Jentsch <d.jentsch@fu-berlin.de>
 * @since 29.04.2014
 */
class SimpleBlackboard extends Blackboard {

  import FormulaSet._

  var DEBUG : Boolean = true

  // For each agent a List of Tasks to execute

  override def getFormulas: List[FormulaStore] = getAll(_ => true)

  override def getAll(p: (Formula) => Boolean): List[FormulaStore] = read { formulas =>
    formulas.values.filter { store =>
      p(store.formula)
    }.toList
  }

  override def getFormulaByName(name: String): Option[FormulaStore] = read { formulas =>
    formulas get name
  }

  override def addFormula(name : String, formula: Formula, role : String) {
    val s = Store.apply(name, formula, role)
    addFormula(s)
    filterAll(_.filter(s))
  }

  override def addFormula(formula : FormulaStore) {
    write { formulas =>
      formulas put (formula.name, formula)
    }
//    TaskSet.agents.foreach{a => TaskSet.addTasks(a,a.filter(formula))}
  }

  override def removeFormula(formula: FormulaStore): Boolean = rmFormulaByName(formula .name)

  override def rmFormulaByName(name: String): Boolean = write { formulas =>
    formulas.remove(name) match {
      case Some(x) => {
        true
      }
      case None => false
    }
  }

  override def rmAll(p: (Formula) => Boolean) = write { formulas =>
      formulas.values foreach (form => if (p(form.formula)) formulas.remove(form.name) else formulas)
  }

  /**
   * Register a new Handler for Formula adding Handlers.
   * @param a - The Handler that is to register
   */
  override def registerAgent(a : Agent) : Unit = {
    TaskSet.addAgent(a)
    freshAgent(a)
  }


  /**
   * Blocking Method to get a fresh Task.
   *
   * @return Not yet executed Task
   */
  override def getTask: Iterable[(Agent,Task)] = TaskSet.getTask

  override def clear() : Unit = {
    rmAll(_ => true)
    TaskSet.clear()
  }

  /**
   * Gives all agents the chance to react to an event
   * and adds the generated tasks.
   *
   * @param t - Function that generates for each agent a set of tasks.
   */
  override def filterAll(t: (Agent) => Unit): Unit = {
    TaskSet.agents.foreach{ a =>
      t(a)
    }
  }

  /**
   * Method that filters the whole Blackboard, if a new agent 'a' is added
   * to the context.
   *
   * @param a - New Agent.
   */
  override protected[blackboard] def freshAgent(a: Agent): Unit = {
    // ATM only formulas trigger events
    getFormulas.foreach{fS => a.filter(fS)}
  }

  override def signalTask() : Unit = TaskSet.signalTask()

  override def collision(t : Task) : Boolean = TaskSet.collision(t)
}

/**
 * Handles multi threaded access to a mutable map.
 */
private object FormulaSet {
  // Formulas

  private val formulaMap = new TrieMap[String, FormulaStore]()

  /**
   * Per se se an action itself. Maybe try different syntax, s.t. we know this one locks,
   * the other one not.
   *
   * Not a Problem ATM: writing the same Key twice may introduce inconsitencies, if two
   * distinct formula stores are used.
   */

  def write[R](action: MMap[String, FormulaStore] => R): R = action(formulaMap)

  def read[R](action: MMap[String, FormulaStore] => R): R = action(formulaMap)
}

private object TaskSet {
  import scala.collection.mutable.HashSet

  var regAgents = mutable.HashMap[Agent,Double]()
  val execTasks = new HashSet[Task] with mutable.SynchronizedSet[Task]

  private val AGENT_SALARY : Double = 5

  /**
   * Notifies process waiting in 'getTask', that there is a new task available.
   */
  protected[blackboard] def signalTask() : Unit = this.synchronized(this.notifyAll())

  def clear() : Unit = {
    regAgents.foreach(_._1.clearTasks())
    execTasks.clear()
  }

  def addAgent(a : Agent) {
    this.synchronized(regAgents.put(a,AGENT_SALARY))
  }

  def agents : List[Agent] = this.synchronized(regAgents.toList.map(_._1))


  /**
   * Gets from any active agent the set of tasks, he wants to execute with his current budget.
   *
   * If the set of tasks is empty he waits until something is added
   * (filter should call signalTask).
   *
   * Of this set we play
   *
   * @return
   */
  def getTask : Iterable[(Agent,Task)] = this.synchronized{

    //
    // 1. Get all Tasks the Agents want to bid on during the auction with their current money
    //
    var r : List[(Double,Agent,Task)] = Nil
    while(r.isEmpty) {
      regAgents.foreach{case (a,budget) => if(a.isActive) a.getTasks(budget).foreach{t => r = (t.bid(budget), a,t) :: r}}
      if(r.isEmpty) this.wait()
    }

    //
    // 2. Bring the Items in Order (sqrt (m) - Approximate Combinatorical Auction, with m - amount of colliding writes).
    //
    // Sort them by their value (Approximate best Solution by : (value) / (sqrt |WriteSet|)).
    // Value should be positive, s.t. we can square the values without changing order
    //
    val queue : List[(Double, Agent,Task)] = r.sortBy{case (b, a,t) => b*b / t.writeSet().size }

    // 3. Take from beginning to front only the non colliding tasks
    // The new tasks should be non-colliding with the existing ones, because they are always filtered.
    var newTask : List[(Agent,Task)] = Nil
    for((price, a, t) <- queue) {
      if(!newTask.exists{e => t.collide(e._2)}) {
        val budget = regAgents.apply(a)
        if(budget >= price) {
          // The task is not colliding with previous tasks and agent has enough money
          newTask = (a, t) :: newTask
          regAgents.put(a,budget - price)
        }
      }
    }

    //
    // 4. After work pay salary and return the tasks
    //
    for((a,b) <- regAgents) regAgents.put(a,b+AGENT_SALARY)

    newTask
  }




  /**
   * Checks if a Task collides with the current executing ones.
   *
   * @param t - Task that could be executed
   *
   * @return true, iff the task collides
   */
  def collision(t : Task) : Boolean = execTasks.exists{e => t.collide(e)}


}
