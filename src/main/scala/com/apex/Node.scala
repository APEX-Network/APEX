/*
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: Node.scala
 *
 * @author: shan.huang@chinapex.com: 2018-7-25 下午1:06@version: 1.0
 */

package com.apex

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.dispatch.{PriorityGenerator, UnboundedStablePriorityMailbox}
import com.apex.common.ApexLogging
import com.apex.consensus._
import com.apex.core._
import com.apex.crypto.UInt256
import com.apex.network._
import com.apex.network.peer.PeerHandlerManager.ReceivableMessages.ReceivedPeers
import com.apex.network.peer.PeerHandlerManagerRef
import com.apex.rpc.{_}
import com.apex.plugins.mongodb.{GasPricePluginRef, MongodbPluginRef}
import com.apex.settings.ApexSettings
import com.apex.utils.NetworkTimeProvider
import com.typesafe.config.Config
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import scala.util.Try

trait NodeMessage

trait AsyncTask

case class NodeStopMessage() extends NodeMessage

case class ProduceTask(task: Blockchain => Unit) extends AsyncTask

object Node {

  class PrioMailbox(settings: ActorSystem.Settings, config: Config)
    extends UnboundedStablePriorityMailbox(
      // Create a new PriorityGenerator, lower prio means more important
      PriorityGenerator {
        // 'highpriority messages should be treated first if possible
        case task: AsyncTask => 0

        case message: NetworkMessage if message.messageType == MessageType.Block => 1

        // 'lowpriority messages should be treated last if possible
        //case 'lowpriority ⇒ 2

        // PoisonPill when no other left
        //case PoisonPill    ⇒ 3

        // We default to 1, which is in between high and low
        case _ => 2
      })

}

class Node(val settings: ApexSettings, config: Config)
          (implicit ec: ExecutionContext)
  extends Actor with ApexLogging {

  log.info("Node starting")

  private val hashCountMax = 10

  private val notification = Notification()

  private val timeProvider = new NetworkTimeProvider(settings.ntp)

  if (settings.plugins.mongodb.enabled) {
    val mongodbPlugin = MongodbPluginRef(settings)
    notification.register(mongodbPlugin)
  }

  private val chain = new Blockchain(settings.chain, settings.consensus, settings.runtimeParas, notification)

  private val peerHandlerManager = PeerHandlerManagerRef(settings.network, timeProvider)
  notification.register(peerHandlerManager)

  private val networkManager = NetworkManagerRef(settings.network, chain.getChainInfo, timeProvider, peerHandlerManager)

  if (settings.miner.privKeys.size > 0) {
    val producer = ProducerRef(settings)
  }

  if (settings.rpc.enabled) {
    if (settings.plugins.mongodb.enabled) {
      val gasPricePlugin = GasPricePluginRef(settings)
      notification.register(gasPricePlugin)
      RpcServer.run(settings, config, self, gasPricePlugin)
    }else RpcServer.run(settings, config, self, null)
  }

  override def receive: Receive = {
    case task: AsyncTask => processAsyncTask(task)
    case message: NetworkMessage => processNetworkMessage(message)
    case cmd: RPCCommand => processRPCCommand(cmd)
    case _: NodeStopMessage => {
      log.info("stopping node")
      //TODO close connections
      //      chain.close()
      //      context.stop(peerHandlerManager)
      //      context.stop(networkManager)
      //      context.stop(producer)
      //      context.stop(self)
    }
    case unknown: Any => {
      println("Unknown msg:")
      println(unknown)
    }
  }

  override def postStop(): Unit = {
    //    if (settings.rpc.enabled) {
    //      RpcServer.stop()
    //    }
    chain.close()
    super.postStop()
  }

  private def processAsyncTask(asyncTask: AsyncTask): Unit = {
    asyncTask match {
      case ProduceTask(task) => task(chain)
      case _ => println(asyncTask)
    }
  }

  private def processRPCCommand(cmd: RPCCommand) = {
    cmd match {
      case GetBlockByIdCmd(id) => {
        val block = Try {
          chain.getBlock(id)
        }
        sender() ! block
      }
      case GetBlockByHeightCmd(height) => {
        val block = Try {
          chain.getBlock(height)
        }
        sender() ! block
      }
      case GetBlockCountCmd() => {
        val blockHeight = Try {
          chain.getHeight()
        }
        sender() ! blockHeight
      }
      case GetBlocksCmd() => {
        val blockList = Try {
          val blockNum = chain.getHeight()
          val blocks = ArrayBuffer.empty[Block]
          for (i <- blockNum - 5 to blockNum) {
            if (i >= 0)
              blocks.append(chain.getBlock(i).get)
          }
          blocks
        }
        sender() ! blockList
      }
      case GetLatesBlockInfoCmd() => {
        val latestHeader = Try {
          chain.getLatestHeader()
        }
        sender() ! latestHeader
      }
      case GetAccountCmd(address) => {
        val account = Try {
          chain.getAccount(address)
        }
        sender() ! account
      }
      case SendRawTransactionCmd(tx) => {
        val sendTx = Try {
          if (tx.verifySignature()) {
            val addResult = chain.addTransactionEx(tx)
            if (addResult.added)
              broadcastInvMsg(new InventoryPayload(InventoryType.Tx, Seq(tx.id)))
            else
              log.error(s"SendRawTransactionCmd addTransaction error, txid=${tx.id.toString}  ${addResult.result}")
            addResult
          }
          else SignatureFail
        }
        sender() ! sendTx
      }
      case GetContractByIdCmd(id) => {
        val receipt = Try {
          chain.getReceipt(id)
        }
        sender() ! receipt
      }
      case GetProducersCmd(listType) => {
        val producers = Try {
          chain.getProducers(listType)
        }
        sender() ! producers
      }
      case GetProducerCmd(addr) => {
        val producer = Try {
          chain.getProducer(addr)
        }
        sender() ! producer
      }
      case GetVotesCmd(addr) => {
        val votes = Try {
          chain.getVote(addr)
        }
        sender() ! votes
      }
    }
  }

  private def processNetworkMessage(message: NetworkMessage) = {
    log.debug(s"Node processNetworkMessage $message")
    message match {
      case VersionMessage(height) => {
        processVersionMessage(message.asInstanceOf[VersionMessage])
      }
      case GetBlocksMessage(blockHashs) => {
        processGetBlocksMessage(message.asInstanceOf[GetBlocksMessage])
      }
      case BlockMessage(block) => {
        processBlockMessage(message.asInstanceOf[BlockMessage])
      }
      case BlocksMessage(blocksPayload) => {
        processBlocksMessage(message.asInstanceOf[BlocksMessage])
      }
      case TransactionsMessage(txsPayload) => {
        processTransactionsMessage(message.asInstanceOf[TransactionsMessage])
      }
      case InventoryMessage(inv) => {
        processInventoryMessage(message.asInstanceOf[InventoryMessage])
      }
      case GetDataMessage(inv) => {
        processGetDataMessage(message.asInstanceOf[GetDataMessage])
      }
      case PeerInfoMessage(peerInfoPayload) => {
        processPeerInfoMessage(message.asInstanceOf[PeerInfoMessage])
      }
    }
  }

  private def processPeerInfoMessage(msg: PeerInfoMessage) = {
    val knowPeers: PeerInfoPayload = msg.peers;
    peerHandlerManager ! ReceivedPeers(knowPeers)
  }

  private def processVersionMessage(msg: VersionMessage) = {
    // first msg, start to sync
    sendGetBlocksMessage()
  }

  private def processGetBlocksMessage(msg: GetBlocksMessage) = {
    def findLatestHash(hashs: Seq[UInt256]): (UInt256, Boolean) = {
      var index = 0
      var found = false
      var latest = UInt256.Zero
      while (index < hashs.size && found == false) {
        if (chain.containsBlock(hashs(index))) {
          latest = hashs(index)
          found = true
        }
        index += 1
      }
      (latest, found)
    }
    //log.info(s"receive GetBlocksMessage  ${msg.blockHashs.hashStart(0).shortString}")
    if (msg.blockHashs.hashStart(0).equals(UInt256.Zero)) {
      sender() ! InventoryMessage(new InventoryPayload(InventoryType.Block, Seq(chain.getLatestHeader.id))).pack()
    }
    else {
      val (hash, found) = findLatestHash(msg.blockHashs.hashStart)
      if (found) {
        val hashs = ArrayBuffer.empty[UInt256]
        //val hashCountMax = 10
        var hashCount = 1
        hashs.append(hash)
        var next = chain.getNextBlockId(hash)
        while (next.isDefined && hashCount < hashCountMax) {
          hashCount += 1
          hashs.append(next.get)
          next = chain.getNextBlockId(next.get)
        }
        //log.info(s"send InventoryMessage, block hash count = ${hashs.size}")
        sender() ! InventoryMessage(new InventoryPayload(InventoryType.Block, hashs)).pack()
      }
    }
  }

  private def processBlockMessage(msg: BlockMessage) = {
    log.debug(s"received a block #${msg.block.height} (${msg.block.shortId})")
    if (msg.block.height() <= chain.getConfirmedHeight()) {
      // minor fork chain, do nothing, should disconnect peer
      log.info(s"received minor fork block, do nothing, ${msg.block.height} ${msg.block.shortId} by ${msg.block.producer.shortAddr}")
    }
    else if (chain.tryInsertBlock(msg.block, true)) {
      broadcastInvMsg(new InventoryPayload(InventoryType.Block, Seq(msg.block.id)))
      log.info(s"success insert block #${msg.block.height} ${msg.block.shortId} by ${msg.block.producer.shortAddr} ")
    }
    else {
      log.error(s"failed insert block #${msg.block.height}, ${msg.block.shortId} by ${msg.block.producer.shortAddr} to db")
      if (!chain.containsBlock(msg.block.id)) {
        // out of sync, or there are fork chains,  to get more blocks
        if (msg.block.height - chain.getHeight < 10) // do not send too many request during init sync
          sendGetBlocksMessage()
      }
    }
  }

  private def processBlocksMessage(msg: BlocksMessage) = {
    var isMinorForkChain = false
    log.info(s"received ${msg.blocks.blocks.size} blocks, first is ${msg.blocks.blocks.head.height} ${msg.blocks.blocks.head.shortId}")
    msg.blocks.blocks.foreach(block => {
      if (block.height() <= chain.getConfirmedHeight()) {
        // minor fork chain, do nothing, should disconnect peer
        isMinorForkChain = true
        log.info(s"received minor fork block, do nothing, ${block.height} ${block.shortId} by ${block.producer.shortAddr}")
      }
      else if (chain.tryInsertBlock(block, true)) {
        log.info(s"success insert block #${block.height} (${block.shortId}) by ${block.producer.shortAddr}")
        if (msg.blocks.blocks.size == 1) // no need to send INV during sync
          broadcastInvMsg(new InventoryPayload(InventoryType.Block, Seq(block.id)))
      }
      else {
        log.debug(s"failed insert block #${block.height}, (${block.shortId}) by ${block.producer.shortAddr} to db")
      }
    })
    // continue get more following blocks
    if (msg.blocks.blocks.size > 1 && isMinorForkChain == false)
      sender() ! GetBlocksMessage(new GetBlocksPayload(Seq(msg.blocks.blocks.last.id), UInt256.Zero)).pack
  }

  private def processTransactionsMessage(msg: TransactionsMessage) = {
    log.debug(s"received ${msg.txs.txs.size} transactions from network")
    val newTxs = ArrayBuffer.empty[UInt256]
    msg.txs.txs.foreach(tx => {
      if (tx.verifySignature()) {
        val addResult = chain.addTransactionEx(tx)
        if (addResult.added)
          newTxs.append(tx.id)
        else
          log.error(s"processTransactionsMessage addTransaction error, txid=${tx.id.toString} ${addResult.result}")
      }
    })
    // for the new txs broadcast INV
    broadcastInvMsg(new InventoryPayload(InventoryType.Tx, newTxs))
  }

  private def processInventoryMessage(msg: InventoryMessage) = {
    val inv = msg.inv
    //log.info(s"received Inventory, inv type ${inv.invType}, hash count ${inv.hashs.size}")
    if (inv.invType == InventoryType.Block) {
      val newBlocks = ArrayBuffer.empty[UInt256]
      inv.hashs.foreach(h => {
        if (chain.containsBlock(h) == false)
          newBlocks.append(h)
      })
      if (newBlocks.size > 0) {
        log.debug(s"send GetDataMessage to request ${newBlocks.size} new blocks.  ${newBlocks(0).shortString}")
        sender() ! GetDataMessage(new InventoryPayload(InventoryType.Block, newBlocks)).pack
      }
      else if (inv.hashs.size >= hashCountMax) {
        log.info(s"all the block hashs in the inv are not new, request more, last hash is ${inv.hashs.last.shortString()} block #${chain.getBlock(inv.hashs.last).get.height()}")
        sender() ! GetBlocksMessage(new GetBlocksPayload(Seq(inv.hashs.last), UInt256.Zero)).pack
      }
    }
    else if (inv.invType == InventoryType.Tx) {
      val newTxs = ArrayBuffer.empty[UInt256]
      inv.hashs.foreach(h => {
        if (chain.getTransactionFromMempool(h).isEmpty)
          newTxs.append(h)
      })
      sender() ! GetDataMessage(new InventoryPayload(InventoryType.Tx, newTxs)).pack
    }
  }

  private def processGetDataMessage(msg: GetDataMessage) = {
    //log.info(s"received GetDataMessage")
    if (msg.inv.invType == InventoryType.Block) {
      val sendBlockNumMax: Int = 10
      var sentBlockNum: Int = 0
      val blocks = ArrayBuffer.empty[Block]
      msg.inv.hashs.foreach(h => {
        val block = chain.getBlock(h)
        if (block != None) {
          if (sentBlockNum < sendBlockNumMax) {
            //sender() ! BlockMessage(block.get).pack
            blocks.append(block.get)
            sentBlockNum += 1
          }
        }
        else
          log.error("received GetDataMessage but block not found")
      })
      if (blocks.size > 0) {
        sender() ! BlocksMessage(new BlocksPayload(blocks)).pack
      }
    }
    else if (msg.inv.invType == InventoryType.Tx) {
      val txs = ArrayBuffer.empty[Transaction]
      msg.inv.hashs.foreach(h => {
        val tx = chain.getTransactionFromMempool(h)
        if (tx.isDefined) {
          txs.append(tx.get)
        }
      })
      if (txs.size > 0) {
        sender() ! TransactionsMessage(new TransactionsPayload(txs)).pack
      }
    }
  }

  private def sendGetBlocksMessage() = {
    var index = chain.getHeight()
    val confirmedHeight = chain.getConfirmedHeight()
    var step = 1
    var count = 0
    val blockLocatorHashes = ArrayBuffer.empty[UInt256]
    while (index > confirmedHeight) {
      blockLocatorHashes.append(chain.getHeader(index).get.id)
      count += 1
      if (count > 10)
        step *= 2
      if (step > 7200)
        step = 7200
      index -= step
    }
    blockLocatorHashes.append(chain.getHeader(confirmedHeight).get.id)
    log.info(s"send GetBlocksMessage. Current status: ${chain.getHeight()} ${chain.getLatestHeader().shortId}")
    sender() ! GetBlocksMessage(new GetBlocksPayload(blockLocatorHashes, UInt256.Zero)).pack
  }

  private def broadcastInvMsg(invPayload: InventoryPayload) = {
    // broadcast to all connected peers
    peerHandlerManager ! InventoryMessage(invPayload)
  }
}

object NodeRef {

  def props(settings: ApexSettings, config: Config)(implicit system: ActorSystem, ec: ExecutionContext): Props = Props(new Node(settings, config)).withMailbox("apex.actor.node-mailbox")

  def apply(settings: ApexSettings, config: Config)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef = system.actorOf(props(settings, config))

  def apply(settings: ApexSettings, config: Config, name: String)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef = system.actorOf(props(settings, config), name)

}
