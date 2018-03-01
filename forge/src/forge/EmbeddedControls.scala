package forge

import language.experimental.macros
import scala.reflect.macros.whitebox

/**
  * Default implementation of Scala control structures.
  *
  * This trait is adapted from the `EmbeddedControls` trait in Scala-Virtualized.
  * See also:
  * [[https://raw.github.com/namin/scala/topic-virt/src/library/scala/EmbeddedControls.scala]]
  *
  * The `EmbeddedControls` trait provides method definitions where
  * calls to the methods are treated by the compiler in a special way.
  * The reason to express these calls as methods is to give embedded
  * DSLs a chance to provide their own definitions and thereby override
  * the standard interpretation of the compiler.
  *
  * Example: When faced with an `if` construct, the `@staged`
  * macro annotation will generate a method call:
  * `__ifThenElse(cond, thenp, elsep)`
  *
  * This method call will be bound to an implementation based on normal
  * rules of scoping.  If it binds to the standard one in this trait,
  * the corresponding macro will replace it by an `If` tree node. If
  * not, the call will be left as it is and a staging or interpreting
  * DSL can take over.
  *
  * @note This is feature experimental.
  * @note None of the above will happen unless you annotate your code with `@staged`.
  */
trait EmbeddedControls {
  import EmbeddedControls._

  // NOTE: Some of the signatures below have "by-val" arguments where
  // one would expect "by-name" arguments.  However, since these are
  // all macros the difference is irrelevant.  Furthermore, there's
  // currently a bug precluding the use of "by-name" parameters in
  // macros (See [[https://issues.scala-lang.org/browse/SI-5778]]).

  /** Val definitions **/
  // TODO: Way to have this not conflict with staged variable creation?
  def __newVar[T](init: T): VarLike[T] = new forge.Ptr[T](init)
  def __assign[T](lhs: VarLike[T], rhs: T): Unit = lhs.__assign(rhs)
  def __readVar[T](v: VarLike[T]): T = v.__read

  def __valName(init: Any, name: String): Unit = ()

  //  def __lazyValDef[T](init: T): T = macro lazyValDefImpl[T]
  //  def __valDef[T](init: T): T = macro valDefImpl[T]

  /** Control structures **/
  def __ifThenElse[T](cond: Boolean, thenBr: T, elseBr: T): T = macro ifThenElseImpl[T]
  def __return(expr: Any): Nothing = macro returnImpl
  def __whileDo(cond: Boolean, body: Unit): Unit = macro whileDoImpl
  def __doWhile(body: Unit, cond: Boolean): Unit = macro doWhileImpl

  def __throw(t: Throwable): Unit = macro throwImpl

  // def __match[A,R](selector: A, cases: Seq[A => R]): R = macro matchImpl[A,R]
  // def __typedCase[A,T,R](bind: T, guard: Boolean, body: R): A => R = macro typedCaseImpl[A,T,R]

  /** `Any` Infix Methods **/
  def infix_+(x1: String, x2: Any): String = macro string_+
  def infix_+(x1: Any, x2: Any): Any = macro any_+ // don't know the return type => should actually never be produced by Virtualizer
  def infix_==(x1: Any, x2: Any): Boolean = macro any_==
  def infix_!=(x1: Any, x2: Any): Boolean = macro any_!=
  def infix_##(x: Any): Int = macro any_##
  def infix_equals(x1: Any, x2: Any): Boolean = macro any_equals
  def infix_hashCode(x: Any): Int = macro any_hashCode
  def infix_asInstanceOf[T](x: Any): T = macro any_asInstanceOf[T]
  def infix_isInstanceOf[T](x: Any): Boolean = macro any_isInstanceOf[T]
  def infix_toString(x: Any): String = macro any_toString
  def infix_getClass(x: Any): Class[_] = macro any_getClass

  /** `AnyRef` Infix Methods **/
  def infix_eq(x1: AnyRef, x2: AnyRef): Boolean = macro anyRef_eq
  def infix_ne(x1: AnyRef, x2: AnyRef): Boolean = macro anyRef_ne
  def infix_notify(x: AnyRef): Unit = macro anyRef_notify
  def infix_notifyAll(x: AnyRef): Unit = macro anyRef_notifyAll
  def infix_synchronized[T](x: AnyRef, body: T): T = macro anyRef_synchronized[T]
  def infix_wait(x: AnyRef): Unit = macro anyRef_wait0
  def infix_wait(x: AnyRef, timeout: Long): Unit = macro anyRef_wait1
  def infix_wait(x: AnyRef, timeout: Long, nanos: Int): Unit = macro anyRef_wait2
  def infix_clone(x: AnyRef): AnyRef = macro anyRef_clone
  def infix_finalize(x: AnyRef): Unit = macro anyRef_finalize

  // Define universal arithmetic for all primitive types
  def infix_+[A<:AnyVal, B<:AnyVal](lhs: A, rhs: B): AnyVal = macro anyVal_+[A,B]

}

/**
  * EmbeddedControls companion object containing macro implementations.
  */
private object EmbeddedControls {

  /** Val/Var Definitions **/
  def newVarImpl[T](c: whitebox.Context)(init: c.Expr[T]): c.Expr[T] = {
    import c.universe._
    c.Expr(q"new Ptr($init)")
  }

  def readVarImpl[T](c: whitebox.Context)(v: c.Expr[T]): c.Expr[T] = v

  def valNameImpl(c: whitebox.Context)(init: c.Expr[Any], name: c.Expr[String]): c.Expr[Unit] = {
    import c.universe._
    c.Expr(q"()")
  }

  def assignImpl[T](c: whitebox.Context)(lhs: c.Expr[T], rhs: c.Expr[T]): c.Expr[Unit] = {
    import c.universe._
    c.Expr(q"$lhs = $rhs")
  }

  //
  //  def lazyValDefImpl[T](c: whitebox.Context)(init: c.Expr[T]): c.Expr[T] = init


  /** Control structures **/

  def ifThenElseImpl[T](c: whitebox.Context)(
    cond: c.Expr[Boolean], thenBr: c.Expr[T], elseBr: c.Expr[T]): c.Expr[T] = {

    import c.universe._
    c.Expr(q"if ($cond) $thenBr else $elseBr")
  }

  def returnImpl(c: whitebox.Context)(expr: c.Expr[Any]): c.Expr[Nothing] = {
    import c.universe._
    c.Expr(q"return $expr")
  }

  def whileDoImpl(c: whitebox.Context)(cond: c.Expr[Boolean], body: c.Expr[Unit]): c.Expr[Unit] = {
    import c.universe._
    c.Expr(q"while ($cond) $body")
  }

  def doWhileImpl(c: whitebox.Context)(body: c.Expr[Unit], cond: c.Expr[Boolean]): c.Expr[Unit] = {
    import c.universe._
    c.Expr(q"do $body while ($cond)")
  }

  def throwImpl(c: whitebox.Context)(t: c.Expr[Throwable]): c.Expr[Nothing] = {
    import c.universe._
    c.Expr(q"throw $t")
  }

  /** `Any` Infix Methods **/

  def string_+(c: whitebox.Context)(x1: c.Expr[String], x2: c.Expr[Any]): c.Expr[String] = {
    import c.universe._
    c.Expr(q"$x1.+($x2)")
  }

  def any_+(c: whitebox.Context)(x1: c.Expr[Any], x2: c.Expr[Any]): c.Expr[Any] = {
    import c.universe._
    c.Expr(q"$x1.+($x2)")
  }

  def any_==(c: whitebox.Context)(x1: c.Expr[Any], x2: c.Expr[Any]): c.Expr[Boolean] = {
    import c.universe._
    c.Expr(q"$x1.==($x2)")
  }

  def any_!=(c: whitebox.Context)(x1: c.Expr[Any], x2: c.Expr[Any]): c.Expr[Boolean] = {
    import c.universe._
    c.Expr(q"$x1.!=($x2)")
  }

  def any_##(c: whitebox.Context)(x: c.Expr[Any]): c.Expr[Int] = {
    import c.universe._
    c.Expr(q"$x.##()")
  }

  def any_equals(c: whitebox.Context)(x1: c.Expr[Any], x2: c.Expr[Any]): c.Expr[Boolean] = {
    import c.universe._
    c.Expr(q"$x1.equals($x2)")
  }

  def any_hashCode(c: whitebox.Context)(x: c.Expr[Any]): c.Expr[Int] = {
    import c.universe._
    c.Expr(q"$x.hashCode()")
  }

  def any_asInstanceOf[T](c: whitebox.Context)(x: c.Expr[Any])(
    implicit tt: c.WeakTypeTag[T]): c.Expr[T] = {

    import c.universe._
    c.Expr(q"$x.asInstanceOf[${tt.tpe}]")
  }

  def any_isInstanceOf[T](c: whitebox.Context)(x: c.Expr[Any])(
    implicit tt: c.WeakTypeTag[T]): c.Expr[Boolean] = {

    import c.universe._
    c.Expr[Boolean](q"$x.isInstanceOf[${tt.tpe}]")
  }

  def any_toString(c: whitebox.Context)(x: c.Expr[Any]): c.Expr[String] = {
    import c.universe._
    c.Expr(q"$x.toString()")
  }

  import scala.language.existentials
  def any_getClass(c: whitebox.Context)(x: c.Expr[Any]): c.Expr[Class[_$1]] forSome { type _$1 } = {
    import c.universe._
    c.Expr(q"$x.getClass()")
  }

  /** `AnyRef` Infix Methods **/

  def anyRef_eq(c: whitebox.Context)(x1: c.Expr[AnyRef], x2: c.Expr[AnyRef]): c.Expr[Boolean] = {
    import c.universe._
    c.Expr(q"$x1.eq($x2)")
  }

  def anyRef_ne(c: whitebox.Context)(x1: c.Expr[AnyRef], x2: c.Expr[AnyRef]): c.Expr[Boolean] = {
    import c.universe._
    c.Expr(q"$x1.ne($x2)")
  }

  def anyRef_notify(c: whitebox.Context)(x: c.Expr[AnyRef]): c.Expr[Unit] = {
    import c.universe._
    c.Expr(q"$x.notify()")
  }

  def anyRef_notifyAll(c: whitebox.Context)(x: c.Expr[AnyRef]): c.Expr[Unit] = {
    import c.universe._
    c.Expr(q"$x.notifyAll()")
  }

  def anyRef_synchronized[T](c: whitebox.Context)(x: c.Expr[AnyRef], body: c.Expr[T]): c.Expr[T] = {
    import c.universe._
    c.Expr(q"$x.synchronized($body)")
  }

  def anyRef_wait0(c: whitebox.Context)(x: c.Expr[AnyRef]): c.Expr[Unit] = {
    import c.universe._
    c.Expr(q"$x.wait()")
  }

  def anyRef_wait1(c: whitebox.Context)(x: c.Expr[AnyRef], timeout: c.Expr[Long]): c.Expr[Unit] = {
    import c.universe._
    c.Expr(q"$x.wait($timeout)")
  }

  def anyRef_wait2(c: whitebox.Context)(x: c.Expr[AnyRef], timeout: c.Expr[Long], nanos: c.Expr[Int]): c.Expr[Unit] = {
    import c.universe._
    c.Expr(q"$x.wait($timeout, $nanos)")
  }

  def anyRef_clone(c: whitebox.Context)(x: c.Expr[AnyRef]): c.Expr[AnyRef] = {
    import c.universe._
    c.Expr(q"$x.clone()")
  }

  def anyRef_finalize(c: whitebox.Context)(x: c.Expr[AnyRef]): c.Expr[Unit] = {
    import c.universe._
    c.Expr(q"$x.finalize()")
  }


  // TODO: move me and add hook for lms
  // TODO: revert import statement to blackbox macros when this is moved
  def anyVal_+[A<:AnyVal, B<:AnyVal](c: whitebox.Context)(lhs: c.Expr[A], rhs: c.Expr[B])(implicit ta: c.WeakTypeTag[A], tb: c.WeakTypeTag[B]): c.Expr[AnyVal] = {
    import c.universe._

    val resultType =
      if (weakTypeOf[A] weak_<:< weakTypeOf[B]) weakTypeOf[B]
      else if (weakTypeOf[B] weak_<:< weakTypeOf[A]) weakTypeOf[A]
      else {
        c.error(c.enclosingPosition, s"Cannot add ${weakTypeOf[A]} and ${weakTypeOf[B]}") // panic (byte/short + char ???)
        weakTypeOf[AnyVal]
      }

    //if (resultType weak_<:< typeOf[Int]) // minimum result type is Int (but maybe short + short should => short)

    c.Expr(q"($lhs.+($rhs):$resultType)")
  }

}
