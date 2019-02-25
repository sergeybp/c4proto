package ee.cone.c4external

import ee.cone.c4actor.Types.SrcId
import ee.cone.c4actor.byjoin.{ByJoinApp, ByJoinFactory, ByPKJoin}
import ee.cone.c4actor.{HashGen, NameMetaAttr, ProdLens}
import ee.cone.c4assemble.Types.{Each, Values}
import ee.cone.c4assemble._
import ee.cone.c4external.ByPKTypes.{ByPKRqId, ToID}
import ee.cone.dbadapter.DBAdapter

import scala.Function.tupled
import scala.reflect.ClassTag

trait ExternalByJoinFactoryApp extends ByJoinApp {
  def dbAdapter: DBAdapter
  def extDBSync: ExtDBSync
  def hashGen: HashGen

  lazy val byJoinFactory: ByJoinFactory = new ExternalByJoinFactoryImpl(dbAdapter, extDBSync, hashGen)
}

class ExternalByJoinFactoryImpl(dbAdapter: DBAdapter, extDBSync: ExtDBSync, hashGen: HashGen) extends ByJoinFactory {
  lazy val extId: String = dbAdapter.externalName
  lazy val extMap: Map[String, Long] = extDBSync.externals

  def byPK[From <: Product](lens: ProdLens[From, List[SrcId]])(implicit ct: ClassTag[From]): ByPKJoin[From, List[SrcId]] =
    new ExternalByPKJoin[From](ct.runtimeClass.asInstanceOf[Class[From]], lens)(extMap, extId, hashGen)
}

class ExternalByPKJoin[From <: Product](
  fromCl: Class[From],
  lens: ProdLens[From, List[SrcId]]
)(
  externalsMap: Map[String, Long],
  externalId: String,
  hashGen: HashGen
) extends ByPKJoin[From, List[SrcId]] {

  private def create[To <: Product](toCl: Class[To]): EachSubAssemble[To] with ValuesSubAssemble[To] with Assemble = {
    externalsMap.get(toCl.getName) match {
      case Some(id) ⇒
        val add = "ext" + lens.metaList.collect { case l: NameMetaAttr ⇒ l.value }.mkString("-")
        val mergeKey = s"${(getClass :: fromCl :: toCl :: Nil).map(_.getName).mkString("-")}#$add"
        val mergeKeyHash = hashGen.generate(mergeKey)
        val baseRq: ByPKExtRequest = ByPKExtRequest("", externalId, mergeKeyHash, "", id)
        val extIdHash = hashGen.generate(externalId :: id :: mergeKeyHash :: Nil)
        val createRq = (id: SrcId) ⇒ {
          val srcId = hashGen.generate(id :: extIdHash :: Nil)
          srcId → baseRq.copy(srcId = srcId, modelSrcId = id)
        }
        new ByPKExternalAssemble(fromCl, toCl, lens, createRq)()
      case None ⇒ new ByPKAssemble(fromCl, toCl, lens)()
    }
  }

  def toEach[To <: Product](implicit ct: ClassTag[To]): EachSubAssemble[To] with Assemble = create(ct.runtimeClass.asInstanceOf[Class[To]])
  def toValues[To <: Product](implicit ct: ClassTag[To]): ValuesSubAssemble[To] with Assemble = create(ct.runtimeClass.asInstanceOf[Class[To]])
}

object ByPKTypes {
  type ToID = SrcId
  type ByPKRqId = SrcId
}

case class InnerByPKRequest(fromSrcId: SrcId)

case class ByPKExtRequest(srcId: SrcId, externalId: SrcId, mergeKeyHash: String, modelSrcId: SrcId, modelId: Long)

@assemble class ByPKExternalAssembleBase[From <: Product, To <: Product](
  fromCl: Class[From],
  toCl: Class[To],
  lens: ProdLens[From, List[SrcId]],
  rqCreation: SrcId ⇒ (SrcId, ByPKExtRequest)
)(
  val mergeKeyAddClasses: List[Class[_]] = List(fromCl, toCl),
  val mergeKeyAddString: String = "ext" + lens.metaList.collect { case l: NameMetaAttr ⇒ l.value }.mkString("-")
) extends EachSubAssemble[To] with ValuesSubAssemble[To] with BasicMergeableAssemble {
  def fromToRq(
    key: SrcId,
    from: Each[From]
  ): Values[(ToID@ns(mergeKey), InnerByPKRequest)] = {
    val rq = InnerByPKRequest(ToPrimaryKey(from))
    for {
      toId ← lens.of(from)
    } yield toId → rq
  }

  def rqToExtRq(
    srcId: SrcId,
    @by[ToID@ns(mergeKey)] rq: Each[InnerByPKRequest]
  ): Values[(ByPKRqId, ByPKExtRequest)] =
    List(rqCreation(srcId))

  def retTo(
    key: SrcId,
    to: Each[To],
    @by[ToID@ns(mergeKey)] rq: Each[InnerByPKRequest]
  ): Values[(ToID@ns(mergeKey), To)] =
    List(rq.fromSrcId → to)

  def result: Result = tupled(retTo _)
}

@assemble class ByPKAssembleBase[From <: Product, To <: Product](
  fromCl: Class[From],
  toCl: Class[To],
  lens: ProdLens[From, List[SrcId]]
)(
  val mergeKeyAddClasses: List[Class[_]] = List(fromCl, toCl),
  val mergeKeyAddString: String = lens.metaList.collect { case l: NameMetaAttr ⇒ l.value }.mkString("-")
) extends EachSubAssemble[To] with ValuesSubAssemble[To] with BasicMergeableAssemble {
  def fromToRq(
    key: SrcId,
    from: Each[From]
  ): Values[(ToID@ns(mergeKey), InnerByPKRequest)] = {
    val rq = InnerByPKRequest(ToPrimaryKey(from))
    for {
      toId ← lens.of(from)
    } yield toId → rq
  }

  def retTo(
    key: SrcId,
    to: Each[To],
    @by[ToID@ns(mergeKey)] rq: Each[InnerByPKRequest]
  ): Values[(ToID@ns(mergeKey), To)] =
    List(rq.fromSrcId → to)

  def result: Result = tupled(retTo _)
}


