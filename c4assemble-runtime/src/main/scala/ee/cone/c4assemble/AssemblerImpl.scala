
package ee.cone.c4assemble

import java.util.Comparator

import Types._
import ee.cone.c4assemble.TreeAssemblerTypes.{MultiSet, Replace}

import scala.annotation.tailrec
import scala.collection.immutable.{Iterable, Map, TreeMap, TreeSet}
import Function.tupled

class PatchMap[K,V,DV](empty: V, isEmpty: V⇒Boolean, op: (V,DV)⇒V) {
  def one(res: Map[K,V], key: K, diffV: DV): Map[K,V] = {
    val prevV = res.getOrElse(key,empty)
    val nextV = op(prevV,diffV)
    if(isEmpty(nextV)) res - key else res + (key → nextV)
  }
  def same(res: Map[K,V], keys: Iterable[K], value: DV): Map[K,V] =
    (res /: keys)((res, key) ⇒ one(res, key, value))
  def many(res: Map[K,V], diff: Iterable[(K,DV)]): Map[K,V] =
    (res /: diff)((res, kv) ⇒ one(res, kv._1, kv._2))
}

//import ValueMerging._

class SimpleIndexValueMergerFactory extends IndexValueMergerFactory {
  def create[R <: Product]: (Values[R],MultiSet[R]) ⇒ Values[R] = {
    val add: PatchMap[R,Int,Int] = new PatchMap[R,Int,Int](0,_==0,(v,d)⇒v+d)
    (value,delta) ⇒ add.same(delta, value, 1).flatMap(ValueMerging.fill _).toList.sortBy(ValueMerging.toPrimaryKey _)
  }
}

object ValueMerging {
  def toPrimaryKey(node: Product): String = node.productElement(0) match {
    case s: String ⇒ s
    case _ ⇒ throw new Exception(s"1st field of ${node.getClass.getName} should be primary key")
  }
  def fill[R](from: (R,Int)): Iterable[R] = {
    val(node,count) = from
    if(count<0) throw new Exception(s"$node gets negative count")
    List.fill(count)(node)
  }
}

/*
case class CachingSeq[R](list: List[R], multiSet: MultiSet[R]) extends Seq[R] {
  def length: Int = list.size
  def apply(idx: Int): R = list(idx)
  def iterator: Iterator[R] = list.iterator
}

class CachingIndexValueMergerFactory(from: Int) extends IndexValueMergerFactory {
  def create[R <: Product]: (Values[R], MultiSet[R]) ⇒ Values[R] = {
    val add: PatchMap[R,Int,Int] = new PatchMap[R,Int,Int](0,_==0,(v,d)⇒v+d)
    (value,delta) ⇒
    val multiSet: MultiSet[R] = value match {
      case value: CachingSeq[R] ⇒ add.many(value.multiSet, delta)
      case s ⇒ add.same(delta, s, 1)
    }
    val list = multiSet.flatMap(ValueMerging.fill).toList.sortBy(ValueMerging.toPrimaryKey(_))
    if(list.size < from) list else CachingSeq(list,multiSet)
  }
}



case class TreeSeq[V](orig: TreeMap[(String,Int),V]) extends Seq[V] {
  def length: Int = orig.size
  def apply(idx: Int): V = orig.view(idx,idx+1).head._2
  def iterator: Iterator[V] = orig.iterator.map(_._2)

}

class TreeIndexValueMergerFactory extends IndexValueMergerFactory {
  def create[R <: Product]: (Values[R], MultiSet[R]) ⇒ Values[R] = {
    val add: PatchMap[R,Int,Int] = new PatchMap[R,Int,Int](0,_==0,(v,d)⇒v+d)
    (value,dMultiMap) ⇒
    val dByPK: Map[String, Map[R, Int]] = dMultiMap.groupBy(ValueMerging.toPrimaryKey(_))

    @tailrec def splitOne(key: (String,Int), from: TreeMap[(String,Int),R], to: Map[R,Int]): TreeMap[(String,Int),R] = {
      val value = from.get(key)
      if(value.isEmpty) from ++ joinK(???,to)
      else splitOne(key match { case (a,b) ⇒ (a,b+1) }, from - key, add.one(to,value.get,1))
    }
    def joinK(prefix: String, from: Map[R,Int]) = ???
    ???
  }







}
*/

/*
case class TreeSeq[V](orig: TreeSet[(V,Int)]) extends Seq[V] {
  def length: Int = orig.size
  def apply(idx: Int): V = orig.view(idx,idx+1).head._1
  def iterator: Iterator[V] = orig.iterator.map(_._1)
}

class TreeIndexValueMergerFactory extends IndexValueMergerFactory {
  def create[R <: Product]: (Values[R], MultiSet[R]) ⇒ Values[R] = {
    (value,dMultiMap) ⇒
      TreeSeq[R](value match {
        case v: TreeSeq[R] ⇒ add(v.orig, dMultiMap)
        case v ⇒ add(TreeSet.empty[(R,Int)], v.map(_→1) ++ dMultiMap)
      })
  }
  private def add[R](orig: TreeSet[(R,Int)], dPairs: Iterable[(R,Int)]): TreeSet[(R,Int)] =
    (orig /: dPairs){ (orig,dPair) ⇒
      val (element,dCount) = dPair
      @tailrec def chk(i: Int): Int = if(orig((element,i))) chk(i+1) else i
      val count = chk(0)
      if(dCount > 0) orig ++ (1 to dCount).map(i⇒(element,count+i))
      else orig -- (dCount to -1).map(i⇒(element,count+i))
    }
}
*/
class IndexFactoryImpl(
  merger: IndexValueMergerFactory
) extends IndexFactory {
  def createJoinMapIndex[T,R<:Product,TK,RK](join: Join[T,R,TK,RK]):
    WorldPartExpression
      with DataDependencyFrom[Index[TK, T]]
      with DataDependencyTo[Index[RK, R]]
  = {
    val add: PatchMap[R,Int,Int] =
      new PatchMap[R,Int,Int](0,_==0,(v,d)⇒v+d)
    val merge = merger.create[R]
    val addNestedPatch: PatchMap[RK,Values[R],MultiSet[R]] =
      new PatchMap[RK,Values[R],MultiSet[R]](Nil,_.isEmpty, merge)
    val addNestedDiff: PatchMap[RK,MultiSet[R],R] =
      new PatchMap[RK,MultiSet[R],R](Map.empty,_.isEmpty,(v,d)⇒add.one(v, d, +1))
    val subNestedDiff: PatchMap[RK,MultiSet[R],R] =
      new PatchMap[RK,MultiSet[R],R](Map.empty,_.isEmpty,(v,d)⇒add.one(v, d, -1))
    new JoinMapIndex[T,TK,RK,R](
      join, addNestedPatch, addNestedDiff, subNestedDiff
    )
  }
}

class JoinMapIndex[T,JoinKey,MapKey,Value<:Product](
  join: Join[T,Value,JoinKey,MapKey],
  addNestedPatch: PatchMap[MapKey,Values[Value],MultiSet[Value]],
  addNestedDiff: PatchMap[MapKey,MultiSet[Value],Value],
  subNestedDiff: PatchMap[MapKey,MultiSet[Value],Value]
) extends WorldPartExpression
  with DataDependencyFrom[Index[JoinKey, T]]
  with DataDependencyTo[Index[MapKey, Value]]
{
  def inputWorldKeys: Seq[WorldKey[Index[JoinKey, T]]] = join.inputWorldKeys
  def outputWorldKey: WorldKey[Index[MapKey, Value]] = join.outputWorldKey

  private def setPart[V](res: World, part: Map[MapKey,V]) =
    (res + (outputWorldKey → part)).asInstanceOf[Map[WorldKey[_],Map[Object,V]]]

  def recalculateSome(
    getIndex: WorldKey[Index[JoinKey,T]]⇒Index[JoinKey,T],
    add: PatchMap[MapKey,MultiSet[Value],Value],
    ids: Set[JoinKey], res: Map[MapKey,MultiSet[Value]]
  ): Map[MapKey,MultiSet[Value]] = {
    val worldParts: Seq[Index[JoinKey,T]] =
      inputWorldKeys.map(getIndex)
    (res /: ids){(res: Map[MapKey,MultiSet[Value]], id: JoinKey)⇒
      val args = worldParts.map(_.getOrElse(id, Nil))
      add.many(res, join.joins(id, args))
    }
  }
  def transform(transition: WorldTransition): WorldTransition = {
    //println(s"rule $outputWorldKey <- $inputWorldKeys")
    val ids = (Set.empty[JoinKey] /: inputWorldKeys)((res,key) ⇒
      res ++ transition.diff.getOrElse(key, Map.empty).keys.asInstanceOf[Set[JoinKey]]
    )
    if (ids.isEmpty){ return transition }
    val prevOutput = recalculateSome(_.of(transition.prev), subNestedDiff, ids, Map.empty)
    val indexDiff = recalculateSome(_.of(transition.current), addNestedDiff, ids, prevOutput)

    //val diffStats = indexDiff.map{case (k,v)⇒s"$k ${v.values.mkString(",")}"}
    //println(s"""indexDiff $diffStats""")
    if (indexDiff.isEmpty){ return transition }

    val currentIndex: Index[MapKey,Value] = outputWorldKey.of(transition.current)
    val nextIndex: Index[MapKey,Value] = addNestedPatch.many(currentIndex, indexDiff)
    val next: World = setPart(transition.current, nextIndex)

    val currentDiff = transition.diff.getOrElse(outputWorldKey,Map.empty).asInstanceOf[Map[MapKey, Boolean]]
    val nextDiff: Map[MapKey, Boolean] = currentDiff ++ indexDiff.transform((_,_)⇒true)
    val diff = setPart(transition.diff, nextDiff)

    WorldTransition(transition.prev, diff, next)
  }
}

case class ReverseInsertionOrderSet[T](contains: Set[T]=Set.empty[T], items: List[T]=Nil) {
  def add(item: T): ReverseInsertionOrderSet[T] = {
    if(contains(item)) throw new Exception(s"has $item")
    ReverseInsertionOrderSet(contains + item, item :: items)
  }
}

object TreeAssemblerImpl extends TreeAssembler {
  def replace: List[DataDependencyTo[_]] ⇒ Replace = rules ⇒ {
    val replace: PatchMap[Object,Values[Object],Values[Object]] =
      new PatchMap[Object,Values[Object],Values[Object]](Nil,_.isEmpty,(v,d)⇒d)
    val add =
      new PatchMap[WorldKey[_],Index[Object,Object],Index[Object,Object]](Map.empty,_.isEmpty,replace.many)
          .asInstanceOf[PatchMap[WorldKey[_],Object,Index[Object,Object]]]
    val expressions/*: Seq[WorldPartExpression]*/ =
      rules.collect{ case e: WorldPartExpression with DataDependencyTo[_] with DataDependencyFrom[_] ⇒ e }
      //handlerLists.list(WorldPartExpressionKey)
    val originals: Set[WorldKey[_]] =
      rules.collect{ case e: OriginalWorldPart[_] ⇒ e.outputWorldKey }.toSet
    println(s"rules: ${rules.size}, originals: ${originals.size}, expressions: ${expressions.size}")
    val byOutput: Map[WorldKey[_], Seq[WorldPartExpression with DataDependencyFrom[_]]] =
      expressions.groupBy(_.outputWorldKey)
    def regOne(
        priorities: ReverseInsertionOrderSet[WorldPartExpression with DataDependencyFrom[_]],
        handler: WorldPartExpression with DataDependencyFrom[_]
    ): ReverseInsertionOrderSet[WorldPartExpression with DataDependencyFrom[_]] = {
      if(priorities.contains(handler)) priorities
      else (priorities /: handler.inputWorldKeys.flatMap{ k ⇒
        byOutput.getOrElse(k,
          if(originals(k)) Nil else throw new Exception(s"undefined $k in $originals")
        )
      })(regOne).add(handler)
    }
    val expressionsByPriority: List[WorldPartExpression] =
      (ReverseInsertionOrderSet[WorldPartExpression with DataDependencyFrom[_]]() /: expressions)(regOne).items.reverse
    replaced ⇒ prevWorld ⇒ {
      val diff = replaced.transform((k,v)⇒v.transform((_,_)⇒true))
      val current = add.many(prevWorld, replaced)
      val transition = WorldTransition(prevWorld,diff,current)
      (transition /: expressionsByPriority) { (transition, handler) ⇒
        handler.transform(transition)
      }.current
    }
  }
}

object AssembleDataDependencies {
  def apply(indexFactory: IndexFactory, assembles: List[Assemble]): List[DataDependencyTo[_]] =
    assembles.flatMap(assemble⇒assemble.dataDependencies(indexFactory))
}