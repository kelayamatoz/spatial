package spatial.traversal

import argon._
import spatial.metadata.access._
import spatial.metadata.control._
import spatial.metadata.memory._
import spatial.lang._
import spatial.node._
import utils.implicits.collections._



case class BufferRecompute(IR: State) extends BlkTraversal {
  override protected def preprocess[R](block: Block[R]): Block[R] = {
    super.preprocess(block)
  }

  override protected def postprocess[R](block: Block[R]): Block[R] = {
    super.postprocess(block)
  }

  override protected def visit[A](lhs: Sym[A], rhs: Op[A]): Unit = rhs match {
    case _: MemAlloc[_,_] if (lhs.getDuplicates.isDefined && !lhs.isFIFO) => 
      dbgs(s"Recomputing depth of $lhs")
      val (_, bufPorts, _) = findMetaPipe(lhs, lhs.readers, lhs.writers)
      val depth = bufPorts.values.collect{case Some(p) => p}.maxOrElse(0) + 1
      if (depth != lhs.instance.depth) {
        dbgs(s"Memory $lhs had depth of ${lhs.instance.depth}, now has depth of $depth")
        lhs.duplicates = lhs.duplicates.map(_.updateDepth(depth)).toSeq
      }
      super.visit(lhs,rhs)

    case _ => super.visit(lhs,rhs)

  }

}
