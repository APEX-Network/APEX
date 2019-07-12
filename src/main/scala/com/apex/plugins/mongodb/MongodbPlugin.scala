package com.apex.plugins.mongodb

import java.time.Instant

import akka.actor.{Actor, ActorContext, ActorRef, Props}
import com.apex.common.ApexLogging
import com.apex.core._
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Sorts._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._
import org.mongodb.scala.bson.BsonDateTime
import com.apex.plugins.mongodb.Helpers._
import com.apex.settings.ApexSettings

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext

class MongodbPlugin(settings: ApexSettings)
                   (implicit ec: ExecutionContext) extends Actor with ApexLogging {

  private val mongoClient: MongoClient = MongoClient(settings.plugins.mongodb.uri)
  private val database: MongoDatabase = mongoClient.getDatabase("apex")
  private val blockCol: MongoCollection[Document] = database.getCollection("block")
  private val txCol: MongoCollection[Document] = database.getCollection("transaction")
  private val accountCol: MongoCollection[Document] = database.getCollection("account")
  private val minerCol: MongoCollection[Document] = database.getCollection("miner")
  private val tpsHourCol: MongoCollection[Document] = database.getCollection("tps_hour")
  private val tpsTenSecCol: MongoCollection[Document] = database.getCollection("tps_tensec")
//  private val gasPriceCol: MongoCollection[Document] = database.getCollection("gasprice")

  init()

  override def postStop(): Unit = {
    log.info("mongodb plugin stopped")
    super.postStop()
  }

  override def receive: Receive = {
    case NewBlockProducedNotify(block) => {}
    case BlockAddedToHeadNotify(blockSummary) => {
      addBlock(blockSummary)
    }
    case BlockConfirmedNotify(block) => {
      blockCol.updateOne(equal("blockHash", block.id.toString), set("confirmed", true)).results()
      block.transactions.foreach(tx => {
        txCol.updateOne(equal("txHash", tx.id.toString), set("confirmed", true)).results()
      })
    }
    //    case AddTransactionNotify(tx) => {
    //      if (findTransaction(tx) == false)
    //        addTransaction(tx, None)
    //    }
    //    case DeleteTransactionNotify(tx) => {
    //      deleteTransaction(tx) //remove transaction from txpool
    //    }
    case ForkSwitchNotify(from, to) => {
      log.info("MongodbPlugin got ForkSwitchNotify")
      from.foreach(block => removeBlock(block))
      to.foreach(block => addBlock(block))
    }
    //    case UpdateAverageGasPrice(averageGasPrice) => {
    //      updateGasPrice(averageGasPrice)
    //    }
    case a: Any => {
      log.info(s"${sender().toString}, ${a.toString}")
    }
  }

  //  def updateGasPrice(gasPrice: String): Unit = {
  //    if (gasPriceCol.find(equal("_id", 1)).results().size > 0) {
  //      gasPriceCol.updateOne(equal("_id", 1), set("average_gp", gasPrice)).results()
  //    } else {
  //      val gasDoc: Document = Document("_id" -> 1,
  //        "average_gp" -> gasPrice)
  //      gasPriceCol.insertOne(gasDoc).results()
  //    }
  //
  //  }
  //
  //  private def findTransaction(tx: Transaction): Boolean = {
  //    txCol.find(equal("txHash", tx.id.toString)).results().size > 0
  //  }

  private def removeBlock(blockSummary: BlockSummary) = {
    val block = blockSummary.block
    log.info(s"MongodbPlugin remove block ${block.height()} , ${block.shortId()}")
    blockCol.deleteOne(equal("blockHash", block.id().toString)).results()
    block.transactions.foreach(tx => {
      txCol.deleteOne(equal("txHash", tx.id.toString)).results()
    })
    updateTps(block, false)
  }


  private def addBlock(blockSummary: BlockSummary) = {
    val block = blockSummary.block
    log.info(s"MongodbPlugin add block ${
      block.height()
    }  ${
      block.shortId()
    }")
    val newBlock: Document = Document(
      "height" -> block.height(),
      "blockHash" -> block.id().toString,
      "timeStamp" -> BsonDateTime(block.timeStamp()),
      "prevBlock" -> block.prev().toString,
      "producer" -> block.producer.address,
      "producerSig" -> block.header.producerSig.toString,
      "version" -> block.header.version,
      "merkleRoot" -> block.header.merkleRoot.toString,
      "txNum" -> block.transactions.size,
      "txHashs" -> block.transactions.map(tx => tx.id.toString),
      "createdAt" -> BsonDateTime(Instant.now.toEpochMilli),
      "confirmed" -> false)

    blockCol.insertOne(newBlock).results()

    addTransactions(blockSummary)

    updateTps(block, true)
  }

  private def updateAccout(tx: Transaction, block: Block) = {
    val option = UpdateOptions()
    option.upsert(true)
    if (tx.from.address.length > 0) {
      accountCol.updateOne(equal("addr", tx.from.address), set("timeStamp", BsonDateTime(block.timeStamp())), option).results()
      accountCol.updateOne(equal("addr", tx.from.address), inc("txCount", 1), option).results()
    }
    accountCol.updateOne(equal("addr", tx.toAddress()), set("timeStamp", BsonDateTime(block.timeStamp())), option).results()
    accountCol.updateOne(equal("addr", tx.toAddress()), inc("txCount", 1), option).results()
    if (tx.txType == TransactionType.Miner && block.height() > 0) {
      minerCol.updateOne(equal("addr", tx.toAddress()), inc("blockCount", 1), option).results()
    }
  }

  private def updateTps(block: Block, isIncrease: Boolean) = {
    val option = UpdateOptions()
    option.upsert(true)

    val tenSec: Long = 10000
    val oneHour: Long = 3600000

    val time10s: Long = block.timeStamp / tenSec * tenSec
    val timeHour: Long = block.timeStamp / oneHour * oneHour

    if (isIncrease) {
      tpsHourCol.updateOne(equal("timeStamp", BsonDateTime(timeHour)), inc("txs", block.transactions.size), option).results()
      tpsTenSecCol.updateOne(equal("timeStamp", BsonDateTime(time10s)), inc("txs", block.transactions.size), option).results()
    } else {
      tpsHourCol.updateOne(equal("timeStamp", BsonDateTime(timeHour)), inc("txs", -block.transactions.size), option).results()
      tpsTenSecCol.updateOne(equal("timeStamp", BsonDateTime(time10s)), inc("txs", -block.transactions.size), option).results()
    }
  }

  private def addTransactions(blockSummary: BlockSummary): Unit = {
    val block = blockSummary.block

    val documents = ArrayBuffer.empty[Document]

    block.transactions.foreach(tx => {
      var newTx: Document = Document(
        "txHash" -> tx.id.toString,
        "type" -> tx.txType.toString,
        "from" -> {
          if (tx.txType == TransactionType.Miner) "" else tx.from.address
        },
        "to" -> {
          if (tx.txType == TransactionType.Deploy)
            tx.getContractAddress().get.address
          else tx.toAddress
        },
        "amount" -> tx.amount.toString,
        "nonce" -> tx.nonce.toString,
        "data" -> tx.data.toString,
        "gasPrice" -> tx.gasPrice.toString,
        "gasLimit" -> tx.gasLimit.longValue(),
        "signature" -> tx.signature.toString,
        "version" -> tx.version,
        "executeTime" -> BsonDateTime(tx.executeTime),
        "createdAt" -> BsonDateTime(Instant.now.toEpochMilli),
        "confirmed" -> false)

      val receiptMap = blockSummary.txReceiptsMap;
      val txReceipt = receiptMap.getOrElse(tx.id(), None)

      if (txReceipt.isDefined) {
        val gasUsed = txReceipt.get.gasUsed
        val status = if (txReceipt.get.error.isEmpty) "Success" else "Fail"
        newTx += ("gasUsed" -> gasUsed.longValue(),
          "fee" -> (tx.gasPrice * gasUsed).toString,
          "status" -> status)
      }

      newTx += ("refBlockHash" -> block.id.toString,
        "refBlockHeight" -> block.height,
        "refBlockTime" -> BsonDateTime(block.timeStamp()))
      updateAccout(tx, block)

      documents.append(newTx)
    })

    txCol.insertMany(documents).results()
  }

  private def init() = {
    log.info("init mongo")

    try {
      if (blockCol.countDocuments().headResult() == 0) {
        log.info("creating mongo db")

        blockCol.createIndex(ascending("height")).results()
        blockCol.createIndex(ascending("blockHash")).results()

        txCol.createIndex(ascending("txHash")).results()
        txCol.createIndex(ascending("refBlockHeight")).results()
        txCol.createIndex(ascending("from")).results()
        txCol.createIndex(ascending("to")).results()

        accountCol.createIndex(ascending("addr")).results()
        accountCol.createIndex(ascending("timeStamp")).results()
        minerCol.createIndex(ascending("addr")).results()
        tpsHourCol.createIndex(ascending("timeStamp")).results()

        tpsTenSecCol.createIndex(ascending("timeStamp")).results()
      }
    }
    catch {
      case e: Throwable => {
        log.error(s"init mongo error: ${
          e.getMessage
        }")
      }
    }
  }
}

object MongodbPluginRef {
  def props(settings: ApexSettings)
           (implicit ec: ExecutionContext): Props = {
    Props(new MongodbPlugin(settings))
  }

  def apply(settings: ApexSettings)
           (implicit system: ActorContext, ec: ExecutionContext): ActorRef = {
    system.actorOf(props(settings))
  }

  def apply(settings: ApexSettings,
            name: String)
           (implicit system: ActorContext, ec: ExecutionContext): ActorRef = {
    system.actorOf(props(settings), name)
  }
}