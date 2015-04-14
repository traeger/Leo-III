package leo
package agents

import leo.datastructures.Pretty
import leo.datastructures.blackboard.{Event, FormulaStore, Blackboard}
import leo.datastructures.context.Context
import leo.modules.output.StatusSZS

import scala.collection.mutable

/**
 * <p>
 * Controlling Entity for an Agent.
 * </p>
 *
 * <p>
 * The AgentController stores all generated Tasks
 * and passes them to the auction scheduler.
 * The publishing order is determined by the implementation.
 * </p>
 * @param a is the Agent to be controlled.
 */
abstract class AgentController(a : Agent) {
  val name : String = a.name

  def openTasks : Int

  private var _isActive : Boolean = true

  def isActive : Boolean = _isActive

  def setActive(a : Boolean) = _isActive = a

  def run(t : Task) = {
    a.run(t)
  }

  def register() = Blackboard().registerAgent(this)
  def unregister() = Blackboard().unregisterAgent(this)

  def kill() = a.kill()

  /*
--------------------------------------------------------------------------------------------
                        COMBINATORICAL AUCTION
--------------------------------------------------------------------------------------------
   */


  /**
   * This method should be called, whenever a formula is added to the blackboard.
   *
   * The filter then checks the blackboard if it can generate tasks from it,
   * that will be stored in the Agent.
   *
   * @param event - Newly added or updated formula
   */
  def filter(event : Event) : Unit


  /**
   *
   * Returns a a list of Tasks, the Agent can afford with the given budget.
   *
   * @param budget - Budget that is granted to the agent.
   */
  def getTasks(budget : Double) : Iterable[Task]

  /**
   * Each task can define a maximum amount of money, they
   * want to posses.
   *
   * A process has to be careful with this barrier, for he
   * may never be doing anything if he has to low money.
   *
   * @return maxMoney
   */
  var maxMoney : Double = 100000

  /**
   * As getTasks with an infinite budget.
   *
   * @return - All Tasks that the current agent wants to execute.
   */
  def getAllTasks : Iterable[Task]

  /**
   *
   * Given a set of (newly) executing tasks, remove all colliding tasks.
   *
   * @param nExec - The newly executing tasks
   */
  def removeColliding(nExec : Iterable[Task]) : Unit

  /**
   * Removes all Tasks
   */
  def clearTasks() : Unit
}

/**
 * <p>
 * Interface for all Agent Implementations.
 * </p>
 *
 * <p>
 * The Agent itself is not a Thread, but a function to be called, at any
 * time its guard is satisfied.
 * </p>
 *
 * <p>
 * To register an Agent, it has to be passed to an AgentController.
 * (Runnable vs. Thread)
 * </p>
 * @author Max Wisniewski
 * @since 5/14/14
 */
abstract class Agent {

  /**
   *
   * @return the name of the agent
   */
  def name : String

  /**
   * This function runs the specific agent on the registered Blackboard.
   */
  def run(t : Task) : Result

  /**
   * This method is called when an agent is killed by the scheduler
   * during execution. This method does standardized nothing.
   *
   * In the case an external Process / Thread is created during the
   * execution of the agent, this method can clean up the processes.
   */
  def kill(): Unit = {}

  /**
   * Triggers the filtering of the Agent.
   *
   * Upon an Event the Agent can generate Tasks, he wants to execute.
   * @param event on the blackboard concerning change of data.
   * @return a List of Tasks the Agent wants to execute.
   */
  def toFilter(event : Event) : Iterable[Task]
}

/**
 * Implements the sorting, selection and saving of tasks of the agent interface.
 *
 * Only the explicit filtering, own tasks and the execution have to be implemeted.
 *
 *
 * The tasks are executed in the order they are generated.
 */
class FifoController(a : Agent) extends AgentController(a) {

  override def setActive(a : Boolean) = {
    super.setActive(a)
    if(a && q.nonEmpty) Blackboard().signalTask()
   }

  override def openTasks : Int = q.size

  override def unregister(): Unit ={
    super.unregister()
    q.synchronized(q.clear())
  }

  protected val q : mutable.Queue[Task] = new mutable.Queue[Task]()

  /**
   * <p>
   * A predicate that distinguishes interesting and uninteresing
   * Formulas for the Handler.
   * </p>
   * @param f - Newly added formula
   * @return true if the formula is relevant and false otherwise
   */
  override def filter(f: Event) : Unit = {
    var done = false
    for(t <- a.toFilter(f)) {
      if (!Blackboard().collision(t)) {
        q.synchronized {
          q.enqueue(t)
        }
        done = true
      }
    }
    if(done) {
      Blackboard().signalTask()
    }
  }

  /**
   *
   * Returns a a list of Tasks, the Agent can afford with the given budget.
   *
   * @param budget - Budget that is granted to the agent.
   */
  override def getTasks(budget: Double): Iterable[Task] = {
    var erg = List[Task]()
    var costs : Double = 0
    q.synchronized {
      for (t <- q) {
        if (costs > budget) return erg
        else {
          costs += t.bid(budget)
          erg = t :: erg
        }
      }
    }
    erg
  }

  /**
   * Removes all Tasks
   */
  override def clearTasks(): Unit = q.synchronized(q.clear())

  /**
   * As getTasks with an infinite budget.
   *
   * @return - All Tasks that the current agent wants to execute.
   */
  override def getAllTasks: Iterable[Task] = q.synchronized(q.iterator.toIterable)

  /**
   *
   * Given a set of (newly) executing tasks, remove all colliding tasks.
   *
   * @param nExec - The newly executing tasks
   */
  override def removeColliding(nExec: Iterable[Task]): Unit = q.synchronized(q.dequeueAll{tbe =>
    nExec.exists{e =>
      val rem = e.writeSet().intersect(tbe.writeSet()).nonEmpty || e.writeSet().intersect(tbe.writeSet()).nonEmpty || e == tbe // Remove only tasks depending on written (changed) data.
      if(rem && e != tbe) Out.trace(s"The task\n  $tbe\n collided with\n  $e\n and was removed.")
      rem
    }
  })
}

/**
 *
 * Implements the selection and storing of the generated Tasks.
 *
 * Only the explicit fitler and the run method have to be implemented.
 *
 *
 * The tasks are executed sorted by their bid starting with the highest bid.
 */
class PriorityController(a : Agent) extends AgentController(a) {

  override def setActive(a : Boolean) = {
    super.setActive(a)
    if(a && q.nonEmpty) Blackboard().signalTask()
  }

  override def unregister(): Unit = {
    super.unregister()
    synchronized{q.clear()}
  }

  // Sort by a fixed amount of money
  protected var q : mutable.PriorityQueue[Task] = new mutable.PriorityQueue[Task]()(Ordering.by{(x : Task) => x.bid(100)})


  override def openTasks : Int = synchronized(q.size)

  /**
   * Calls the internal toFilter method and inserts all generated tasks to the priority queue.
   *
   * @param f - Raised Event.
   */
  override def filter(f: Event) : Unit = {
    var done = false
    val it = a.toFilter(f).iterator
    while(it.hasNext) {
      val t = it.next()
      if (!Blackboard().collision(t)) {
        synchronized {
          q.enqueue (t)
        }
        done = true
      }
    }
    if(done) {
      Blackboard().signalTask()
    }
  }

    /**
     *
     * Returns a a list of Tasks, the Agent can afford with the given budget.
     *
     * @param budget - Budget that is granted to the agent.
     */
  override def getTasks(budget: Double): Iterable[Task] = {
    var erg = List[Task]()
    var costs : Double = 0
    q.synchronized {
      for (t <- q) {
        // TODO Change to iterator since for is inefficient
        if (costs > budget) return erg
        else {
          costs += t.bid(budget)
          erg = t :: erg
        }
      }
    }
    erg
  }

  /**
   * Removes all Tasks
   */
  override def clearTasks(): Unit = q.synchronized {q.clear()}

  /**
   * As getTasks with an infinite budget.
   *
   * @return - All Tasks that the current agent wants to execute.
   */
  override def getAllTasks: Iterable[Task] = synchronized(q.iterator.toIterable)

  /**
   *
   * Given a set of (newly) executing tasks, remove all colliding tasks.
   *
   * @param nExec - The newly executing tasks
   */
  override def removeColliding(nExec: Iterable[Task]): Unit = {
    synchronized {
      q = q.filter { tbe =>
        nExec.forall{e =>
          val take = e.writeSet().intersect(tbe.writeSet()).isEmpty && e.writeSet().intersect(tbe.writeSet()).isEmpty && e != tbe
          if(!take && e != tbe) Out.trace(s"The task\n  $tbe\n collided with\n  $e\n and was therefore removed.")
          take
        }
      }
    }
  }

}




/**
 * Common trait for all Agent Task's. Each agent specifies the
 * work it can do.
 *
 * The specific fields and accessors for the real task will be in
 * the implementation.
 *
 * @author Max Wisniewski
 * @since 6/26/14
 */
abstract class Task extends Pretty {

  /**
   * Prints a short name of the task
   * @return
   */
  def name : String

  /**
   *
   * Returns a set of all Formulas that are read for the task.
   *
   * @return Read set for the Task.
   */
  def readSet() : Set[FormulaStore]

  /**
   *
   * Returns a set of all Formulas, that will be written by the task.
   *
   * @return Write set for the task
   */
  def writeSet() : Set[FormulaStore]

  /**
   * Defines a set of Contexts on which the task will
   * write.
   *
   * @return set of all contexts the task will manipulate
   */
  def contextWriteSet() : Set[Context] = Set.empty

  /**
   * Checks for two tasks, if they are in conflict with each other.
   *
   * @param t2 - Second Task
   * @return true, iff they collide
   */
  def collide(t2 : Task) : Boolean = {
    val t1 = this
    if(t1 equals t2) true
    else {
      t1.readSet().intersect(t2.writeSet()).nonEmpty ||
        t2.readSet().intersect(t1.writeSet()).nonEmpty ||
        t2.writeSet().intersect((t1.writeSet())).nonEmpty ||
        t2.contextWriteSet().intersect((t1.contextWriteSet())).nonEmpty
    }
  }

  /**
   *
   * Defines the gain of a Task, defined for
   * a specific agent.
   *
   * @return - Possible profit, if the task is executed
   */
  def bid(budget : Double) : Double
}

/**
 * Common Trait, for the results of tasks.
 *
 * @author Max Wisniewski
 * @since 6/26/12
 */
trait Result {

  /**
   * A set of new formulas created by the task.
   *
   * @return New formulas to add
   */
  def newFormula() : Set[FormulaStore]

  /**
   * A mapping of formulas to be changed.
   *
   * @return Changed formulas
   */
  def updateFormula() : Map[FormulaStore, FormulaStore]

  /**
   * A set of formulas to be removed.
   *
   * @return Deleted formulas
   */
  def removeFormula() : Set[FormulaStore]

  /**
   * Set of all new or updated Contexts
   * @return
   */
  def updatedContext() : Set[Context]

  /**
   * List of stati to update
   *
   * @return updated stati
   */
  def updateStatus() : List[(Context,StatusSZS)]
}

/**
 * Simple container for the implementation of result.
 *
 * @param nf - New formulas
 * @param uf - Update formulas
 * @param rf - remove Formulas
 */
class StdResult(nf : Set[FormulaStore], uf : Map[FormulaStore,FormulaStore], rf : Set[FormulaStore]) extends Result{
  override def newFormula() : Set[FormulaStore] = nf
  override def updateFormula() : Map[FormulaStore,FormulaStore] = uf
  override def removeFormula() : Set[FormulaStore] = rf
  override def updatedContext() : Set[Context] = Set.empty
  override def updateStatus() : List[(Context,StatusSZS)] = List()
}

object EmptyResult extends Result{
  override def newFormula() : Set[FormulaStore] = Set.empty
  override def updateFormula() : Map[FormulaStore,FormulaStore] = Map.empty
  override def removeFormula() : Set[FormulaStore] = Set.empty
  override def updatedContext(): Set[Context] = Set.empty
  override def updateStatus(): List[(Context, StatusSZS)] = List()
}