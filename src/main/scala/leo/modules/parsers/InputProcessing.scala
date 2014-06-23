package leo.modules.parsers

import scala.language.implicitConversions

import leo.datastructures.tptp.Commons._
import leo.datastructures.tptp.Commons.{Term => TPTPTerm}
import leo.datastructures.internal.{Signature, IsSignature, Term, Type, Kind}
import leo.datastructures.internal.HOLBinaryConnective
import leo.datastructures.internal.HOLUnaryConnective

import Term.{mkAtom}
import Type.{mkFunType,mkType,∀,mkVarType, typeKind}
import leo.datastructures.tptp.Commons.THFAnnotated
import leo.datastructures.tptp.Commons.TPIAnnotated
import leo.datastructures.tptp.Commons.TFFAnnotated

/**
 * Processing module from TPTP input.
 * Declarations are inserted into the given Signature,
 * terms are returned in internal term representation.
 *
 * @author Alexander Steen
 * @since 18.06.2014
 */
object InputProcessing {
  // (Formula name, Term, Formula Role)
  type Result = (String, Term, String)

  /**
   * Assumptions:
   * - To guarantee coherence, the processing is invoked in the right order (i.e. included files are parsed an processed before all
   * following tptp statements)
   *
   * Side effects: All declarations that are not representable as term (e.g. type declarations, subtype declarations) are
   * inserted into the signature `sig` while processing.
   *
   * @param sig The signature declarations are inserted into
   * @param input The TPTP formula to process/translate
   * @return A List of tuples (name, term, role) of translated terms
   */
  def processAll(sig: Signature)(input: Seq[AnnotatedFormula]): Seq[Result] = ???

  def process(sig: Signature)(input: AnnotatedFormula): Option[Result] = {
    input match {
      case _:TPIAnnotated => processTPI(sig)(input.asInstanceOf[TPIAnnotated])
      case _:THFAnnotated => processTHF(sig)(input.asInstanceOf[THFAnnotated])
      case _:TFFAnnotated => processTFF(sig)(input.asInstanceOf[TFFAnnotated])
      case _:FOFAnnotated => processFOF(sig)(input.asInstanceOf[FOFAnnotated])
      case _:CNFAnnotated => processCNF(sig)(input.asInstanceOf[CNFAnnotated])
    }
  }


  //////////////////////////
  // TPI Formula processing
  //////////////////////////

  def processTPI(sig: Signature)(input: TPIAnnotated): Option[Result] = ???


  //////////////////////////
  // THF Formula processing
  //////////////////////////

  def processTHF(sig: Signature)(input: THFAnnotated): Option[Result] = ???

  //////////////////////////
  // TFF Formula processing
  //////////////////////////

  def processTFF(sig: Signature)(input: TFFAnnotated): Option[Result] = {
    import leo.datastructures.tptp.tff.{Logical, TypedAtom, Sequent, AtomicType}

    input.formula match {
      // Logical formulae can either be terms (axioms, conjecture, ...) or definitions.
      case Logical(lf) if input.role == "definition" => {
                                                          val (defName, defDef) = processTFFDef(sig)(lf)
                                                          sig.addDefined(defName, defDef, defDef.ty)
                                                          None
                                                        }
      case Logical(lf) => Some((input.name, processTFF0(sig)(lf), input.role))
      // Typed Atoms are top-level declarations, put them into signature
      case TypedAtom(atom, ty) => {
        convertTFFType(sig)(ty, Seq.empty) match {
          case Left(ty) => sig.addUninterpreted(atom, ty)
          case Right(k) => sig.addUninterpreted(atom, k)
        }
        None
      }
      // Sequents
      case Sequent(_, _) => throw new IllegalArgumentException("Processing of TFF sequents not yet implemented")
    }


  }

  import leo.datastructures.tptp.tff.{LogicFormula => TFFLogicFormula}
  // Formula definitions
  protected[parsers] def processTFFDef(sig: Signature)(input: TFFLogicFormula): (String, Term) = {
    import leo.datastructures.tptp.tff.Atomic
    input match {
      case Atomic(Equality(Func(name, Nil),right)) => (name, ???)  // TODO
      case _ => throw new IllegalArgumentException("Malformed definition")
    }
  }

  // Ordinary terms
  protected[parsers] def processTFF0(sig: Signature)(input: TFFLogicFormula): Term = {
    import leo.datastructures.tptp.tff.{Binary, Quantified, Unary, Inequality, Atomic, Cond, Let}
    input match {
      case Binary(left, conn, right) => processTFFBinaryConn(conn).apply(processTFF0(sig)(left),processTFF0(sig)(right))
      case Quantified(q, vars, matrix) => ???
      case Unary(conn, formula) => processTFFUnary(conn).apply(processTFF0(sig)(formula))
      case Inequality(left, right) => {
        val (l,r) = (processTerm(sig)(left),processTerm(sig)(right))
        import leo.datastructures.internal.{===, Not}
        Not(===(l,r))
      }
      case Atomic(atomic) => processAtomicFormula(sig)(atomic)
      case Cond(cond, thn, els) => ???
      case Let(binding, in) => ???
    }
  }

  import leo.datastructures.tptp.tff.{BinaryConnective => TFFBinaryConnective}
  protected[parsers] def processTFFBinaryConn(conn: TFFBinaryConnective): HOLBinaryConnective = {
    import leo.datastructures.tptp.tff.{<=> => TFFEquiv, Impl => TFFImpl, <= => TFFIf, | => TFFOr, & => TFFAnd}
    import leo.datastructures.internal.{<=> => equiv, Impl => impl, <= => i_f, ||| => or, & => and}

    conn match {
      case TFFEquiv => equiv
      case TFFImpl  => impl
      case TFFIf    => i_f
      case TFFOr    => or
      case TFFAnd   => and
      case _ => throw new IllegalArgumentException("Binary connective "+conn.toString +" not yet implemented") // TODO: Include TPTP connectives
    }
  }

  import leo.datastructures.tptp.tff.{UnaryConnective => TFFUnaryConnective}
  protected[parsers] def processTFFUnary(conn: TFFUnaryConnective): HOLUnaryConnective = {
    import leo.datastructures.tptp.tff.{Not => TFFNot}
    import leo.datastructures.internal.{Not => not}
    conn match {
      case TFFNot => not
    }
  }

  import leo.datastructures.tptp.tff.{Quantifier => TFFQuantifier}
  protected[parsers] def processTFFUnary(conn: TFFQuantifier): HOLUnaryConnective = {
    import leo.datastructures.tptp.tff.{! => TFFAll, ? => TFFAny}
    import leo.datastructures.internal.{Forall => forall, Exists => exists}
    conn match {
      case TFFAll => forall
      case TFFAny => exists
    }
  }

  // Type processing
  import leo.datastructures.tptp.tff.{Type => TFFType}
  type TFFBoundReplaces = Seq[Variable]
  protected[parsers] def convertTFFType(sig: Signature)(tffType: TFFType, replace: TFFBoundReplaces): Either[Type,Kind] = {
    import leo.datastructures.tptp.tff.{AtomicType,->,*,QuantifiedType}
    tffType match {
      // "AtomicType" constructs: Type variables, Base types, type kinds, or type/kind applications
      case AtomicType(ty, List()) if ty.charAt(0).isUpper => mkVarType(replace.length - replace.indexOf(ty))  // Type Variable
      case AtomicType(ty, List()) if ty == "$tType" => typeKind // kind *
      case AtomicType(ty, List())  => mkType(sig.meta(ty).key)  // Base type
      case AtomicType(_, _) => throw new IllegalArgumentException("Processing of applied types not implemented yet") // TODO
      // Function type / kind
      case ->(tys) => { // Tricky here: It might be functions of "sort" * -> [], * -> *, [] -> [], [] -> *
                        // We only plan to support variant 1 (polymorphism),2 (constructors), 3 (ordinary functions) in a medium time range (4 is dependent type)
                        // Case 1 is captured by 'case QuantifiedType' due to TFF1's syntax
                        // So, only consider case 3 for now, but keep case 2 in mind
        val convertedTys = tys.map(convertTFFType(sig)(_, replace))
        require(convertedTys.forall(_.isLeft), "Constructors are not yet supported, but kind found inside a function: " +tffType.toString) // TODO
        mkFunType(convertedTys.map(_.left.get)) // since we only want case 3
      }
      // Product type / kind
      case *(_) => throw new IllegalArgumentException("Processing of product types not implemented yet") // TODO
      // Quantified type
      case QuantifiedType(vars, body) => {
        val vars2 = vars.map(_._1)
        // In the following: body must be a type, otherwise we could not quantify over it
        vars2.foldRight(convertTFFType(sig)(body,vars2).left.get)({case (_,b) => ∀(b)}) // NOTE: this is only allowed on top-level
        // thats why we ignore the previous vars
      }
    }
  }

  implicit def kindToTypeOrKind(k: Kind): Either[Type, Kind] = Right(k)
  implicit def typeToTypeOrKind(ty: Type): Either[Type, Kind] = Left(ty)

  //////////////////////////
  // FOF Formula processing
  //////////////////////////

  def processFOF(sig: Signature)(input: FOFAnnotated): Option[Result] = ???

  //////////////////////////
  // CNF Formula processing
  //////////////////////////

  def processCNF(sig: Signature)(input: CNFAnnotated): Option[Result] = ???


  ////////////////////////////
  // Common 'term' processing
  ////////////////////////////
  def processTerm(sig: Signature)(input: TPTPTerm): Term = ???

  def processAtomicFormula(sig: Signature)(input: AtomicFormula): Term = input match {
    case Plain(func) => processTerm(sig)(func)
    case DefinedPlain(func) => processTerm(sig)(func)
    case SystemPlain(func) => processTerm(sig)(func)
    case Equality(left,right) => {
      import leo.datastructures.internal.===
      ===(processTerm(sig)(left),processTerm(sig)(right))
    }
  }
}

