
package ee.cone.c4gate

import ee.cone.c4actor._
import ee.cone.c4assemble.Assemble

class HttpGatewayApp extends ServerApp
  with EnvConfigApp with VMExecutionApp
  with KafkaProducerApp with KafkaConsumerApp
  with ParallelObserversApp with TreeIndexValueMergerFactoryApp
  with InternetForwarderApp
  with HttpServerApp
  with SSEServerApp
  with NoAssembleProfilerApp
  with MortalFactoryApp
  with ManagementApp
  with SnapshotMakingApp
{
  def httpHandlers: List[RHttpHandler] = //todo secure
    new HttpGetSnapshotHandler(snapshotLoader,authKey) ::
    new HttpGetPublicationHandler(worldProvider) ::
    pongHandler ::
    new HttpPostHandler(qMessages,worldProvider) ::
    Nil
  def sseConfig: SSEConfig = NoProxySSEConfig(config.get("C4STATE_REFRESH_SECONDS").toInt)
}

// I>P -- to agent, cmd>evl
// >P -- post, sse status
// Sn> -- to neo
// S0>W -- static content

trait SnapshotMakingApp extends ToStartApp with AssemblesApp {
  def snapshotLoader: SnapshotLoader
  def consuming: Consuming
  def toUpdate: ToUpdate
  //
  lazy val rawSnapshotLoader: RawSnapshotLoader = fileRawSnapshotLoader
  lazy val snapshotMaker: SnapshotMaker = fileSnapshotMaker
  //
  private lazy val fileSnapshotMaker: SnapshotMakerImpl =
    new SnapshotMakerImpl(snapshotConfig, snapshotLoader, fileRawSnapshotLoader, fullSnapshotSaver, txSnapshotSaver, consuming, toUpdate)
  private lazy val dbDir = "db4"
  private lazy val snapshotConfig: SnapshotConfig =
    new FileSnapshotConfigImpl(dbDir)()
  private lazy val fullSnapshotSaver: SnapshotSaver =
    new SnapshotSaverImpl("snapshots",new FileRawSnapshotSaver(dbDir))
  private lazy val txSnapshotSaver: SnapshotSaver =
    new SnapshotSaverImpl("snapshot_txs",new FileRawSnapshotSaver(dbDir))
  private lazy val fileRawSnapshotLoader: FileRawSnapshotLoader =
    new FileRawSnapshotLoader(dbDir)
  //
  override def toStart: List[Executable] =
    new SafeToRun(fileSnapshotMaker) :: super.toStart
  override def assembles: List[Assemble] =
    new SnapshotMakingAssemble(getClass.getName,fileSnapshotMaker) :: super.assembles
}
