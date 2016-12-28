package ee.cone.c4vdom_impl

import java.util.Base64

import ee.cone.c4vdom._

/*trait View {
  def view(path: String): List[ChildPair[_]]
}*/



class CurrentVDomImpl(
  diff: Diff,
  jsonToString: JsonToString,
  wasNoValue: WasNoVDomValue,
  child: ChildPairFactory
) extends CurrentVDom {
  //def until(value: Long) = if(value < until) until = value
  private def relocate(state: VDomState, message: Map[String,String]) =
    for(hash ← message.get("X-r-location-hash") if hash != state.hashFromAlien)
      yield state.copy(hashFromAlien = hash, hashTarget = hash)
  //dispatches incoming message // can close / set refresh time
  private def dispatch(state: VDomState, message: Map[String,String]) =
    for(pathStr <- message.get("X-r-vdom-path")) yield {
      if(state.until <= 0) throw new Exception("invalid VDom")
      val path = pathStr.split("/").toList match {
        case "" :: parts => parts
        case _ => Never()
      }
      (message.get("X-r-action"), ResolveValue(state.value, path)) match {
        case (Some("click"), Some(v: OnClickReceiver)) => v.onClick.get(state)
        case (Some("change"), Some(v: OnChangeReceiver)) =>
          val decoded = UTF8String(Base64.getDecoder.decode(message("X-r-vdom-value-base64")))
          v.onChange.get(state,decoded)
        case v => throw new Exception(s"$path ($v) can not receive $message")
      }
    }
  private def setLastMessage(state: VDomState, message: Map[String,String]) =
    for(connection ← message.get("X-r-connection"); index ← message.get("X-r-index"))
      yield state.copy(ackFromAlien = connection :: index :: Nil)
  private def handlers =
    List[(VDomState,Map[String,String])⇒Option[VDomState]](setLastMessage,relocate,dispatch)
  def fromAlien(state: VDomState, message: Map[String,String]): VDomState =
    (state /: handlers)((state,f)⇒f(state).getOrElse(state))
  def toAlien(state: VDomState)(view: ()⇒List[ChildPair[_]]): (VDomState,List[(String,String)]) = if(
    state.value != wasNoValue &&
    state.until > System.currentTimeMillis &&
    state.hashOfLastView == state.hashFromAlien
  ) (state,Nil) else {
    val rootAttributes = List("ackMessage" → ("ackMessage" :: state.ackFromAlien))
    val rootElement = RootElement(rootAttributes)
    val nextDom = child("root", rootElement, view()) //state.hashFromAlien
      .asInstanceOf[VPair].value
    val nextState =
      state.copy(value=nextDom, until=Long.MaxValue, hashOfLastView=state.hashFromAlien)
    val diffTree = diff.diff(state.value, nextState.value)
    val diffCommands = diffTree.map(d=>("showDiff", jsonToString(d))).toList
    val relocateCommands = if(state.hashFromAlien==state.hashTarget) Nil
      else List("relocateHash"→state.hashTarget)
    (nextState, diffCommands ::: relocateCommands)
  }


  /*
  private lazy val PathSplit = """(.*)(/[^/]*)""".r
  private def view(pathPrefix: String, pathPostfix: String): List[ChildPair[_]] =
    Single.option(handlerLists.list(ViewPath(pathPrefix))).map(_(pathPostfix))
      .getOrElse(pathPrefix match {
        case PathSplit(nextPrefix,nextPostfix) =>
          view(nextPrefix,s"$nextPostfix$pathPostfix")
      })
  */
}

case class RootElement(conf: List[(String,List[String])]) extends VDomValue {
  def appendJson(builder: MutableJsonBuilder) = {
    builder.startObject()
    builder.append("tp").append("span")
    conf.foreach{ case (k,v) ⇒
      builder.append(k)
      builder.startArray()
      v.foreach(builder.append)
      builder.end()
    }
    builder.end()
  }
}

object ResolveValue {
  def apply(value: VDomValue, path: List[String]): Option[VDomValue] =
    if(path.isEmpty) Some(value) else Some(value).collect{
      case m: MapVDomValue => m.pairs.collectFirst{
        case pair if pair.jsonKey == path.head => apply(pair.value, path.tail)
      }.flatten
    }.flatten
}