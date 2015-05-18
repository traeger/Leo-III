package leo.modules.proofCalculi

import leo.datastructures._
import leo.modules.normalization.{NegationNormal, Simplification}
import leo.modules.output.Output




trait ParamodStep extends Output{

  /**
   * Executes a step of the Paramodulation.
   *
   * @param c - First clause
   * @param d - Second clause
   * @param lc - Term in first clause
   * @param ld - Literal in second clause (not contained)
   * @param s - Substitution of the paramodulation
   * @return new generated clause
   */
  def exec(c : Clause, d : Clause, lc : Term, ld : Literal, s :Unification#Substitute) : Clause

  def find(c1: Clause, c2: Clause, comp: Unification): Option[(Term, Literal, Unification#Substitute)]
}

object PropParamodulation extends ParamodStep{
  /**
   *
   * Executes Propositional Resolution in Superposition
   *
   *
   * C[l']    D \/ [l] = \alpha  s(l') = s(l)
   * --------------------------------------------
   * (C[\alpha] \/ D) s
   *
   * @param c - First Clause
   * @param d - Second Clause
   * @param lc - Term to be replaced in first clause
   * @param ld  - Literal of form [l] = \alpha, NOT CONTAINED IN d
   * @param s - s(lc) = s(ld.term) according to comparrison
   * @return
   */
  override def exec(c: Clause, d: Clause, lc: Term, ld: Literal, s: Unification#Substitute): Clause = {

    val alpha: Term = if (ld.polarity) LitTrue else LitFalse
    val cSub = c.replace(lc, alpha)
    val merged = cSub.merge(d)
    //    leo.Out.severe("What: "+lc.pretty)
    //    leo.Out.severe("By: "+alpha.pretty)
    val res = NegationNormal.normalize(Simplification.normalize(Clause.mkClause(merged.substitute(s._1).lits, s._2 ++ merged.implicitBindings, Derived)))
    return TrivRule.triv(TrivRule.teqf(Simp(res)))
  }

  /**
   * TODO: Use Term comparison. Currently simple equality is used.
   *
   * @param c1 - First clause
   * @param c2 - Second clause
   * @param comp - comparison object, if two terms are unifiable
   * @return (t,l,s), where t is the selected first term, l is the literal and s is a substitiontion, that makes both equal.
   */
  override def find(c1: Clause, c2: Clause, comp: Unification): Option[(Term, Literal, Unification#Substitute)] = {
    if(c1.lits.isEmpty || c2.lits.isEmpty) return None

    val lits = c2.lits.iterator
    while (lits.hasNext) {
      val lit = lits.next()
      val t = lit.term
      if (c1.lits.exists { l => (l.term.occurrences.keys.toSet).contains(t)})
        return Some(t, lit, (Subst.id, Nil))
    }

    return None
  }

  override def output: String = "Paramod-Propositional"
}

  object Paramodulation extends ParamodStep{

    private def decomp(l: Literal): Option[(Term, Term)] = l.term match {
      case ===(t1,t2) => Some(t1,t2)
      case _ => None
    }

    /**
     *
     * Executes Propositional Resolution in Superposition
     *
     *
     * C[l']    D \/ [l = r] = T  s(l') = s(l)
     * --------------------------------------------
     *            (C[r] \/ D) s
     *
     * @param c - First Clause
     * @param d - Second Clause
     * @param lc - Term to be replaced in first clause
     * @param ld  - Literal in the form [l = r] = T, NOT CONTAINED IN d
     * @param s - s(lc) = s(ld.term) according to comparrison
     * @return
     */
    override def exec(c: Clause, d: Clause, lc: Term, ld: Literal, s: Unification#Substitute): Clause = {
      val (l,r) = decomp(ld).get


      val cSub = c.replace(lc, r)
      val merged = cSub.merge(d)
      //    leo.Out.severe("What: "+lc.pretty)
      //    leo.Out.severe("By: "+alpha.pretty)
      val res = NegationNormal.normalize(Simplification.normalize(Clause.mkClause(merged.substitute(s._1).lits, s._2 ++ merged.implicitBindings, Derived)))
      return TrivRule.triv(TrivRule.teqf(Simp(res)))
    }

    /**
     * TODO: Use Term comparison. Currently simple equality is used.
     *
     * @param c1 - First clause
     * @param c2 - Second clause
     * @param comp - comparison object, if two terms are unifiable
     * @return (t,l,s), where t is the selected first term, l is the literal and s is a substitiontion, that makes both equal.
     */
    override def find(c1: Clause, c2: Clause, comp: Unification): Option[(Term, Literal, Unification#Substitute)] = {

      if(c1.lits.isEmpty || c2.lits.isEmpty) return None

      val lits = c2.lits.iterator
      while (lits.hasNext) {
        val lit = lits.next()
        decomp(lit) match {
          case Some((l,r)) if lit.polarity =>
            if (c1.lits.exists { lt => (lt.term.occurrences.keys.toSet).contains(l)})
              return Some(l, lit, (Subst.id, Nil))
          case _ =>
        }
      }

      return None
    }

    override def output: String = "Paramod-Full"
  }


trait CalculusRule[Hint] {
  type HintType = Hint

  def name: String
}
trait UnaryCalculusRule[Res, Hint] extends ((Clause, Hint) => Res) with CalculusRule[Hint] {

  def canApply(cl: Clause): (Boolean, Hint)
}

trait BinaryCalculusRule[Res, Hint] extends ((Clause, Clause, Hint) => Res) with CalculusRule[Hint] {
  def canApply(cl1: Clause, cl2: Clause): (Boolean, Hint)
}


object NewParamod extends BinaryCalculusRule[Set[Clause], (Set[(Literal, (Term, Term), Term)],Set[(Literal, (Term, Term), Term)])] {
  type EqLit = Literal  // the equality literal that causes the paramodulation
  type TTR = Term // term to replace in literal (since it unifies with a side of the EqLit)
  type DirEq = (Term, Term)  // the terms of that equality sorted by: (term that unifies with TTR, term that will be replaced for TTR)

  type sideHint = Set[(EqLit, DirEq, TTR)]


  def mayUnify(s: Term, t: Term): Boolean = {
    // Match case on head symbols:
    // flex-flex always works*, flex-rigid also works*, rigid-rigid only in same symbols
    // * = if same type
    if (s.headSymbol == t.headSymbol)
      true
    else if (s.headSymbol.isVariable && s.headSymbol.ty == t.headSymbol.ty)
      true
    else if (t.headSymbol.isVariable && t.headSymbol.ty == s.headSymbol.ty)
      true
    else
      false
  }

  def canApply(cl1: Clause, cl2: Clause): (Boolean, HintType) = {
    var left_termsThatMayUnify: Set[(EqLit, DirEq, TTR)] = Set()
    var right_termsThatMayUnify: Set[(EqLit, DirEq, TTR)] = Set()

    val (eqLits1, eqLits2) = (cl1.eqLits, cl2.eqLits)
    // for equalities from left clause
    val eqLits1It = eqLits1.iterator

    while(eqLits1It.hasNext) {
      val eqLit = eqLits1It.next()
      val (l,r) = eqLit.eqComponents.get

      val lits2 = cl2.lits.iterator

      while(lits2.hasNext) {
        val otherLit = lits2.next()
        val subterms = otherLit.term.occurrences.keySet.iterator
        while (subterms.hasNext) {
          val st = subterms.next()
          if (mayUnify(st, l)) {
            left_termsThatMayUnify = left_termsThatMayUnify + ((eqLit, (l, r), st))
          }
          if (mayUnify(st, r)) {
            left_termsThatMayUnify = left_termsThatMayUnify + ((eqLit, (r, l), st))
          }
        }
      }
    }

    val eqLits2It = eqLits1.iterator

    while(eqLits2It.hasNext) {
      val eqLit = eqLits2It.next()
      val (l,r) = eqLit.eqComponents.get

      val lits1 = cl1.lits.iterator

      while(lits1.hasNext) {
        val otherLit = lits1.next()
        val subterms = otherLit.term.occurrences.keySet.iterator
        while (subterms.hasNext) {
          val st = subterms.next()
          if (mayUnify(st, l)) {
            right_termsThatMayUnify = right_termsThatMayUnify + ((eqLit, (l, r), st))
          }
          if (mayUnify(st, r)) {
            right_termsThatMayUnify = right_termsThatMayUnify + ((eqLit, (r, l), st))
          }
        }
      }
    }
    (right_termsThatMayUnify.nonEmpty || left_termsThatMayUnify.nonEmpty,(left_termsThatMayUnify,right_termsThatMayUnify))
  }

  def apply(cl1: Clause, cl2: Clause, hint: (Set[(EqLit, DirEq, TTR)],Set[(EqLit, DirEq, TTR)])) = {
    var newCls : Set[Clause] = Set()

    // for equalities from left clause
    val leftHint = hint._1
    val leftIt = leftHint.iterator
    while (leftIt.hasNext) {
      val (eqLit, (left,right), ttr) = leftIt.next()
      val restLits = cl1.lits.filterNot(_ == eqLit)
      val uniConstraint = Literal.mkUniLit(left, ttr)
      val replLits = cl2.replace(ttr, right).lits
      newCls = newCls + Clause.mkClause(restLits ++ replLits :+ uniConstraint, Derived)
    }

    // for equalities from right clause
    val rightHint = hint._2
    val rightIt = rightHint.iterator
    while (rightIt.hasNext) {
      val (eqLit, (left,right), ttr) = rightIt.next()
      val restLits = cl2.lits.filterNot(_ == eqLit)
      val uniConstraint = Literal.mkUniLit(left, ttr)
      val replLits = cl1.replace(ttr, right).lits
      newCls = newCls + Clause.mkClause(restLits ++ replLits :+ uniConstraint, Derived)
    }

    newCls
  }

  def name = "new_paramod"
}



/**
 * {{{
 *    C \/ [Q U^k]^\alpha , P general binding for `hdSymb`
 *   ------------------------------------
 *     V[Q/P] \/ [P U^k]^\alpha
 * }}}
 *
 * Hint not needed since its implemented in clause.
 */
class PrimSubst(hdSymbs: Set[Term]) extends UnaryCalculusRule[Set[Clause], Unit] {
  val name = "prim_subst"

    def canApply(cl: Clause) = (cl.flexHeadLits.nonEmpty, ())

    def apply(cl: Clause, hint: Unit): Set[Clause] = hdSymbs.map{hdSymb =>
      val vars = cl.flexHeadLits.map(_.term.headSymbol)
      vars.map{case hd =>
        val binding = HuetsPreUnification.partialBinding(hd.ty, hdSymb)
        val subst = Subst.singleton(hd.metaIndices.head, binding)
        Simplification.normalize(Clause.mkClause(cl.lits.map(_.termMap(_.substitute(subst).betaNormalize)), cl.implicitBindings, Derived, ClauseAnnotation(this, cl)))
      }
    }.flatten
}

object StdPrimSubst extends PrimSubst(Set(Not, LitFalse, LitTrue, |||))



object BoolExt extends UnaryCalculusRule[Clause, (Seq[Literal], Seq[Literal])] {
  def canApply(cl: Clause): (Boolean, (Seq[Literal], Seq[Literal])) = {
    var it = cl.lits.iterator
    var boolExtLits: Seq[Literal] = Seq()
    var otherLits: Seq[Literal] = Seq()
    while (it.hasNext) {
      val lit = it.next()
      import leo.datastructures.impl.Signature
      lit.term match {
        case (left === _) if left.ty == Signature.get.o => boolExtLits = boolExtLits :+ lit
        case _ => otherLits = otherLits :+ lit
      }
    }
    (boolExtLits.nonEmpty, (boolExtLits, otherLits))
  }
  
  def apply(v1: Clause, boolExtLits_otherLits: (Seq[Literal], Seq[Literal])) = {
    val boolExtLits = boolExtLits_otherLits._1
    val otherLits = boolExtLits_otherLits._2
    var groundLits: Seq[Literal] = Seq()
    val it = boolExtLits.iterator
    while (it.hasNext) {
      val lit = it.next()
      val (left, right) = ===.unapply(lit.term).get
      groundLits = groundLits :+ Literal.mkLit(<=>(left,right).full_δ_expand.betaNormalize, lit.polarity)
    }
    NegationNormal.normalize(Simplification.normalize(Clause.mkClause(otherLits ++ groundLits, Derived)))
  }

  def name = "bool_ext"
}

object FuncExt extends UnaryCalculusRule[Clause, (Seq[Literal], Seq[Literal])] {
  def canApply(cl: Clause): (Boolean, (Seq[Literal], Seq[Literal])) = {
    var it = cl.lits.iterator
    var boolExtLits: Seq[Literal] = Seq()
    var otherLits: Seq[Literal] = Seq()
    while (it.hasNext) {
      val lit = it.next()
      lit.term match {
        case (left === _) if left.ty.isFunType => boolExtLits = boolExtLits :+ lit
        case _ => otherLits = otherLits :+ lit
      }
    }
    (boolExtLits.nonEmpty, (boolExtLits, otherLits))
  }

  def apply(cl: Clause, boolExtLits_otherLits: (Seq[Literal], Seq[Literal])) = {
    val boolExtLits = boolExtLits_otherLits._1
    val otherLits = boolExtLits_otherLits._2
    var groundLits: Seq[Literal] = Seq()
    val it = boolExtLits.iterator
    while (it.hasNext) {
      val lit = it.next()
      val (left, right) = ===.unapply(lit.term).get
      if (lit.polarity) {
        // FuncPos, insert fresh var
        val freshVar = Term.mkFreshMetaVar(left.ty._funDomainType)
        groundLits = groundLits :+ Literal.mkEqLit(Term.mkTermApp(left, freshVar).betaNormalize,Term.mkTermApp(right, freshVar).betaNormalize)
      } else {
        // FuncNeg, insert skolem term
        // get freevars of clause
        val fvs = cl.freeVars.toSeq
        val fv_types = fvs.map(_.ty)
        import leo.datastructures.impl.Signature
        val skConst = Term.mkAtom(Signature.get.freshSkolemVar(Type.mkFunType(fv_types, left.ty._funDomainType)))
        val skTerm = Term.mkTermApp(skConst, fvs)
        groundLits = groundLits :+ Literal.mkUniLit(Term.mkTermApp(left, skTerm).betaNormalize,Term.mkTermApp(right, skTerm).betaNormalize)
      }
    }
    Clause.mkClause(otherLits ++ groundLits, Derived)
  }

  def name = "func_ext"
}


  // TODO: Optimize
  object Simp {
    def apply (c : Clause) : Clause = {
      import leo.modules.normalization.Simplification

      val litNorm = Simplification.normalize(c).mapLit(flipNeg)

      // Remove unnused Quantifiers.

      val looseBounds : Set[Int] = litNorm.map(_.term.looseBounds).toSet.flatten
      val implicitQuan : Seq[Type] = c.implicitBindings

      val misBound = looseBounds.diff(Range.apply(1,implicitQuan.size).toSet)

      val liftLits = litNorm.map(_.termMap(_.closure(liftMissingBound(misBound, implicitQuan.size)).betaNormalize))

      return Clause.mkClause(liftLits, removeBounds(implicitQuan, misBound, implicitQuan.length), Derived)
    }

    private def flipNeg(l : Literal) : Literal = l.term match {
      case Not(f) => l.flipPolarity.termMap(_ => f)
      case _ => l
    }

    /*
     * Returns subsitution and positions of implicitQuan to delete
     */
    private def liftMissingBound(m : Set[Int], maxBind : Int) : Subst = {
      var pos : Int = 1
      var free : Int = 1
      var s = Subst.id
      while(pos <= maxBind) {
        s = s.cons(BoundFront(free))    // If it is not contained, it will never substitute this value
        if(m.contains(pos)) free += 1
      }
      s
    }

    private def removeBounds(b : Seq[Type], m : Set[Int], pos : Int) : Seq[Type] = b match {
      case Seq() => Seq()
      case x +: xs if m.contains(pos) => removeBounds(xs, m, pos-1)
      case x +: xs => x +: removeBounds(xs, m, pos-1)
    }
  }
