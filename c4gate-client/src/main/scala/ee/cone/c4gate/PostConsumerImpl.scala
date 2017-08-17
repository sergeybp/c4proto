package ee.cone.c4gate

import ee.cone.c4actor.Types.SrcId
import ee.cone.c4actor._
import ee.cone.c4assemble.Types.Values
import ee.cone.c4assemble.{Assemble, assemble, by}
import ee.cone.c4gate.AlienProtocol.PostConsumer

@assemble class PostConsumerAssemble(actorName: String) extends Assemble {

  type WasConsumer = SrcId
  def wasConsumers(
    key: SrcId,
    consumers: Values[PostConsumer]
  ): Values[(WasConsumer,PostConsumer)] =
    for(c ← consumers if c.consumer == actorName) yield WithPK(c)

  type NeedConsumer = SrcId
  def needConsumers(
    key: SrcId,
    consumers: Values[LocalPostConsumer]
  ): Values[(NeedConsumer,PostConsumer)] =
    for(c ← consumers.distinct)
      yield WithPK(PostConsumer(s"$actorName/${c.condition}", actorName, c.condition))

  def syncConsumers(
    key: SrcId,
    @by[WasConsumer] wasConsumers: Values[PostConsumer],
    @by[NeedConsumer] needConsumers: Values[PostConsumer]
  ): Values[(SrcId,TxTransform)] =
    if(wasConsumers.toList == needConsumers.toList) Nil
    else List(WithPK(SimpleTxTransform(key,
      wasConsumers.flatMap(LEvent.delete) ++ needConsumers.flatMap(LEvent.update)
    )))
}

