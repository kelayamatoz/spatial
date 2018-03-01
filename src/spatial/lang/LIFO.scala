package spatial.lang

import core._
import forge.tags._
import spatial.node._

import scala.collection.mutable.MutableList

@ref class LIFO[A:Bits] extends Top[LIFO[A]] with LocalMem[A,LIFO] with Ref[MutableList[Any],LIFO[A]] {
  val tA: Bits[A] = Bits[A]
  val ev: LIFO[A] <:< LocalMem[A,LIFO] = implicitly[LIFO[A] <:< LocalMem[A,LIFO]]
}
object LIFO {
  @api def apply[A:Bits](depth: I32): LIFO[A] = stage(LIFONew(depth))
}
