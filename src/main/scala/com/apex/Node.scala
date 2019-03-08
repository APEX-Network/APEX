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
import com.apex.rpc.{ExecResult, _}
import com.apex.plugins.mongodb.MongodbPluginRef
import com.apex.settings.ApexSettings
import com.apex.utils.NetworkTimeProvider
import com.typesafe.config.Config
import play.api.libs.json.Json

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext

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
    RpcServer.run(settings, config, self)
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

  private def resultString(execResult: ExecResult): String = {
    Json.toJson(execResult).toString()
  }

  private def processRPCCommand(cmd: RPCCommand) = {
    try {
      cmd match {
        case GetBlockByIdCmd(id) => {
          val res = Json.toJson(chain.getBlock(id).get).toString()
          sender() ! resultString(new ExecResult(result = res))
        }
        case GetBlockByHeightCmd(height) => {
          /*import Block.blockWrites*/
          val res = Json.toJson(chain.getBlock(height).get).toString()
          sender() ! resultString(new ExecResult(result = res))
        }
        case GetBlockCountCmd() => {
          sender() ! resultString(new ExecResult(result = chain.getHeight().toString()))
        }
        case GetLatesBlockInfoCmd() => {
          val res = Json.toJson(chain.getLatestHeader()).toString()
          sender() ! resultString(new ExecResult(result = res))
        }
        case GetBlocksCmd() => {
          val blockNum = chain.getHeight()
          val blocks = ArrayBuffer.empty[Block]
          for (i <- blockNum - 5 to blockNum) {
            if (i >= 0)
              blocks.append(chain.getBlock(i).get)
          }
          val res = Json.toJson(blocks).toString()
          sender() ! resultString(new ExecResult(result = res))
        }
        case GetAccountCmd(address) => {
          val res = Json.toJson(chain.getAccount(address)).toString()
          sender() ! resultString(new ExecResult(result = res))
        }
        case SendRawTransactionCmd(tx) => {
          var exec = false
          if (tx.verifySignature() && tx.verify()) {
            peerHandlerManager ! InventoryMessage(new InventoryPayload(InventoryType.Tx, Seq(tx.id)))
            if (chain.addTransaction(tx))
              exec = true
          }
          sender() ! resultString(new ExecResult(result = exec.toString))
        }
        case GetContractByIdCmd(id) => {
          val res = Json.toJson(chain.getReceipt(id)).toString()
          sender() ! resultString(new ExecResult(result = res))
        }
        case SetGasLimitCmd(gasLimit) => {
          sender() ! resultString(new ExecResult(result = chain.setGasLimit(gasLimit).toString()))
        }
        case GetGasLimitCmd() => {
          sender() ! resultString(new ExecResult(result = chain.getGasLimit().toString))
        }
        case GetProducersCmd(listType) => {
          val res = Json.toJson(chain.getProducers(listType)).toString()
          sender() ! resultString(new ExecResult(result = res))
        }
        case GetProducerCmd(addr) => {
          val res = Json.toJson(chain.getProducer(addr)).toString()
          sender() ! resultString(new ExecResult(result = res))
        }
        case _ => {
          // 返回404
          sender() ! resultString(new ExecResult(false, 404, "can not found"))
        }
      }
    } catch {
      case e: Exception => {
        println(e.getMessage)
        // 返回500
        sender() ! resultString(new ExecResult(false, 500, e.getMessage))
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
    def findLatestHash(hashs: Seq[UInt256]): UInt256 = {
      var index = 0
      var found = false
      var latest = chain.getHeader(0).get.id
      while (index < hashs.size && found == false) {
        if (chain.containsBlock(hashs(index))) {
          latest = hashs(index)
          found = true
        }
        index += 1
      }
      latest
    }
    //log.info(s"receive GetBlocksMessage  ${msg.blockHashs.hashStart(0).shortString}")
    if (msg.blockHashs.hashStart(0).equals(UInt256.Zero)) {
      sender() ! InventoryMessage(new InventoryPayload(InventoryType.Block, Seq(chain.getLatestHeader.id))).pack()
    }
    else {
      val hash = findLatestHash(msg.blockHashs.hashStart)
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

  private def processBlockMessage(msg: BlockMessage) = {
    log.debug(s"received a block #${msg.block.height} (${msg.block.shortId})")
    if (chain.tryInsertBlock(msg.block, true)) {
      peerHandlerManager ! InventoryMessage(new InventoryPayload(InventoryType.Block, Seq(msg.block.id())))
      log.info(s"success insert block #${msg.block.height} ${msg.block.shortId} by ${msg.block.header.producer.address.substring(0, 7)} ")
    } else {
      log.error(s"failed insert block #${msg.block.height}, ${msg.block.shortId} by ${msg.block.header.producer.address.substring(0, 7)} to db")
      if (!chain.containsBlock(msg.block.id)) {
        // out of sync, or there are fork chains, try to get more blocks
        if (msg.block.height - chain.getHeight < 10) // do not send too many request during init sync
          sendGetBlocksMessage()
      }
    }
  }

  private def processBlocksMessage(msg: BlocksMessage) = {
    log.info(s"received ${msg.blocks.blocks.size} blocks, first is ${msg.blocks.blocks.head.height} ${msg.blocks.blocks.head.shortId}")
    msg.blocks.blocks.foreach(block => {
      if (chain.tryInsertBlock(block, true)) {
        log.info(s"success insert block #${block.height} (${block.shortId})")
        // no need to send INV during sync
        //peerHandlerManager ! InventoryMessage(new Inventory(InventoryType.Block, Seq(block.id())))
      } else {
        log.debug(s"failed insert block #${block.height}, (${block.shortId}) to db")
      }
    })
    if (msg.blocks.blocks.size > 1)
      sender() ! GetBlocksMessage(new GetBlocksPayload(Seq(msg.blocks.blocks.last.id), UInt256.Zero)).pack
  }

  private def processTransactionsMessage(msg: TransactionsMessage) = {
    log.debug(s"received ${msg.txs.txs.size} transactions from network")
    //producer ! ReceivedNewTransactions(txsPayload.txs)
    msg.txs.txs.foreach(tx => {
      if (tx.verifySignature())
        chain.addTransaction(tx)
    })
    // TODO: for the new txs broadcast INV
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
      else if (inv.hashs.size == hashCountMax) {
        log.info("all the block hashs in the inv are not new, request more")
        sender() ! GetBlocksMessage(new GetBlocksPayload(Seq(inv.hashs.last), UInt256.Zero)).pack
      }
    }
    else if (inv.invType == InventoryType.Tx) {
      // val newTxs = ArrayBuffer.empty[UInt256]
      // TODO: only request the txs that we don't have
      sender() ! GetDataMessage(new InventoryPayload(InventoryType.Tx, inv.hashs)).pack
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
    var step = 1
    var count = 0
    val blockLocatorHashes = ArrayBuffer.empty[UInt256]
    while (index > 0) {
      blockLocatorHashes.append(chain.getHeader(index).get.id)
      count += 1
      if (count > 10)
        step *= 2
      if (step > 7200)
        step = 7200
      index -= step
    }
    blockLocatorHashes.append(chain.getHeader(0).get.id)
    log.info(s"send GetBlocksMessage. Current status: ${chain.getHeight()} ${chain.getLatestHeader().shortId}")
    sender() ! GetBlocksMessage(new GetBlocksPayload(blockLocatorHashes, UInt256.Zero)).pack
  }
}

object NodeRef {

  def props(settings: ApexSettings, config: Config)(implicit system: ActorSystem, ec: ExecutionContext): Props = Props(new Node(settings, config)).withMailbox("apex.actor.node-mailbox")

  def apply(settings: ApexSettings, config: Config)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef = system.actorOf(props(settings, config))

  def apply(settings: ApexSettings, config: Config, name: String)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef = system.actorOf(props(settings, config), name)

}
