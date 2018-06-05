package spatial.transform

import argon._
import argon.transform.MutateTransformer
import spatial.lang._
import spatial.node._
import spatial.internal._
import spatial.util.shouldMotion
import spatial.traversal.AccelTraversal
import spatial.data._
import spatial.util._

/** Converts inner pipes that contain switches into innerpipes with enabled accesses.
  * Also squashes outer unit pipes that contain only one child
  */
case class FlatteningTransformer(IR: State) extends MutateTransformer with AccelTraversal {

  private var enables: Set[Bit] = Set.empty

  override def transform[A:Type](lhs: Sym[A], rhs: Op[A])(implicit ctx: SrcCtx): Sym[A] = rhs match {
    case AccelScope(body) => inAccel{
      if (inHw && lhs.isInnerControl && lhs.children.length >= 1) {
        dbgs(s"Transforming $lhs = $rhs")
        val body2 = wrapPrimitives(body)
        val lhs2 = stageWithFlow(AccelScope(body2)){lhs2 => transferData(lhs,lhs2) }
        lhs2      
      } else super.transform(lhs,rhs)
    }

    case UnitPipe(en, body) if (inHw && lhs.isInnerControl && lhs.children.length >= 1) =>
      dbgs(s"Transforming $lhs = $rhs")
      val body2 = wrapPrimitives(body)
      val lhs2 = stageWithFlow(UnitPipe(en, body2)){lhs2 => transferData(lhs,lhs2) }
      lhs2

    case StateMachine(ens,start,notDone,action,nextState)  if (inHw && lhs.isInnerControl && lhs.children.length >= 1) =>
      dbgs(s"Transforming $lhs = $rhs")
      val action2 = wrapPrimitives(action)
      implicit val D = nextState.result.tp match {case Bits(bA) => bA}
      val lhs2 = stageWithFlow(StateMachine(ens, start, notDone, action2.asLambda1, nextState)(D)){lhs2 => transferData(lhs,lhs2) }
      lhs2

    case UnrolledForeach(ens,cchain,body,iters,valids) if (inHw && lhs.isInnerControl && lhs.children.length >= 1) =>
      dbgs(s"Transforming $lhs = $rhs")
      val body2 = wrapPrimitives(body)
      val lhs2 = stageWithFlow(UnrolledForeach(ens, cchain, body2, iters, valids)){lhs2 => transferData(lhs,lhs2) }
      lhs2

    case UnrolledReduce(ens,cchain,body,iters,valids) if (inHw && lhs.isInnerControl && lhs.children.length >= 1) =>
      dbgs(s"Transforming $lhs = $rhs")
      val body2 = wrapPrimitives(body)
      val lhs2 = stageWithFlow(UnrolledReduce(ens, cchain, body2, iters, valids)){lhs2 => transferData(lhs,lhs2) }
      lhs2

    case _ => super.transform(lhs,rhs)
  }


  private def extractPrimitives(body: Block[_])(implicit ctx: SrcCtx): Unit = {
    body.stms.map{
      case Primitive(s) => dbgs(s"visiting $s - ${s.op}");visit(s)
      case sym @ Op(op: Switch[_]) => 
        sym.children.map(_.s.get).map{sc => val Op(op) = sc; implicit val typeInfo = sc.tp; extractPrimitives(sc.blocks.head)}
        implicit val typeInfo = sym.tp
        val Op(rhs) = sym
        if (op.R.isBits) createOneHotMux(sym)
        ()
    }
  }

  private def wrapPrimitives(body: Block[Void])(implicit ctx: SrcCtx): Block[Void] = {
    val body2 = stageScope(f(body.inputs),body.options){
      extractPrimitives(body)
      assert(body.result.isVoid)
      // substituteSym(body.result)
      void
    }
    body2
  }

  private def createOneHotMux[A:Type](lhs: Sym[A])(implicit ctx: SrcCtx): Unit = {
    Type[A] match {
      case Bits(b) =>
        implicit val bA: Bits[A] = b
        val Op(Switch(selects,body)) = lhs
        val stms = body.stms.map(_.asInstanceOf[A])
        val cases = stms.collect{case Op(op:SwitchCase[_]) => op.asInstanceOf[SwitchCase[A]]  }
        val vals = cases.map{cas => substituteSym(cas.body.result).unbox }
        val sels = selects.map(sel => substituteSym(sel).unbox)
        val ohmux = oneHotMux[A](sels, vals)
        dbgs(s"staging $ohmux (<- $lhs) - ${ohmux.op}")
        register(lhs -> ohmux)
      case _ =>
    }

  }

}


