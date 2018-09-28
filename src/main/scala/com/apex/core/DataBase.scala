/*
 *
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: DataBase.scala
 *
 * @author: ruixiao.xiao@chinapex.com: 18-9-27 下午12:02@version: 1.0
 *
 */

package com.apex.core

import com.apex.common.ApexLogging
import com.apex.crypto.{Fixed8, UInt160, UInt256}
import com.apex.settings.DataBaseSettings
import com.apex.storage.LevelDbStorage

import scala.collection.mutable.{Set, Map}

class DataBase(settings: DataBaseSettings) extends ApexLogging {
  private val db = LevelDbStorage.open(settings.dir)

  //  private val headerStore = new HeaderStore(db, settings.cacheSize)
  //  private val heightStore = new HeightStore(db, settings.cacheSize)
  //  private val txStore = new TransactionStore(db, settings.cacheSize)
  private val accountStore = new AccountStore(db, settings.cacheSize)
  //  private val addressStore = new AddressStore(db)
  //  private val blkTxMappingStore = new BlkTxMappingStore(db, settings.cacheSize)
  //  private val headBlkStore = new HeadBlockStore(db)
  //private val utxoStore = new UTXOStore(db, 10)
  private val nameToAccountStore = new NameToAccountStore(db, settings.cacheSize)

  private var isPendingBlock = false

  def nameExists(name: String): Boolean = {
    nameToAccountStore.contains(name)
  }

  def registerExists(register: UInt160): Boolean = {
    accountStore.contains(register)
  }

  def getAccount(address: UInt160): Option[Account] = {
    accountStore.get(address)
  }

  def getBalance(address: UInt160): Option[scala.collection.immutable.Map[UInt256, Fixed8]] = {
    accountStore.get(address).map(_.balances)
  }

  def applyTransaction(transaction: Transaction): Boolean = {
    if (isPendingBlock) {

    }
    else {
      startSession()
      isPendingBlock = true
    }

    // TODO  check and apply this tx

    false
  }

  def applyBlock(block: Block): Boolean = {
    var applied = false
    if (isPendingBlock) {
      rollBack()
    }
    if (verifyBlock(block)) {
      startSession()
      if (applyBlockToDB(block)) {
        applied = true
      }
      else {
        rollBack()
      }
    }
    applied
  }

  def startSession(): Unit = {
    db.newSession()
  }

  def rollBack(): Unit = {
    db.rollBack()
    isPendingBlock = false
  }

  def commit(revision: Int): Unit = {
    db.commit(revision)
  }

  def commit(): Unit = {
    db.commit()
  }

  private def applyBlockToDB(block: Block): Boolean = {
    def calcBalancesInBlock(balances: Map[UInt160, Map[UInt256, Fixed8]], spent: Boolean,
                            address: UInt160, amounts: Fixed8, assetId: UInt256) = {
      val amount = if (spent) -amounts else amounts
      balances.get(address) match {
        case Some(balance) => {
          balance(assetId) += amount
        }
        case None => balances.put(address, Map((assetId, amount)))
      }
    }

    def updateAccout(accounts: Map[UInt160, Account], tx: Transaction) = {
      // TODO
    }

    try {
      db.batchWrite(batch => {
        //headerStore.set(block.header.id, block.header, batch)
        //heightStore.set(block.header.index, block.header.id, batch)
        //headBlkStore.set(HeadBlock.fromHeader(block.header), batch)
        //prodStateStore.set(latestProdState, batch)
        val blkTxMapping = BlkTxMapping(block.id, block.transactions.map(_.id))
        //blkTxMappingStore.set(block.id, blkTxMapping, batch)
        val accounts = Map.empty[UInt160, Account]
        val balances = Map.empty[UInt160, Map[UInt256, Fixed8]]
        val nonceIncrease = Map.empty[UInt160, Long]
        block.transactions.foreach(tx => {
          //txStore.set(tx.id, tx, batch)
          calcBalancesInBlock(balances, true, tx.fromPubKeyHash, tx.amount, tx.assetId)
          calcBalancesInBlock(balances, false, tx.toPubKeyHash, tx.amount, tx.assetId)
          val noncePlus = nonceIncrease.get(tx.from.pubKeyHash).getOrElse(0L) + 1L
          nonceIncrease.put(tx.from.pubKeyHash, noncePlus)
          updateAccout(accounts, tx)
        })
        balances.foreach(p => {
          val account = accountStore.get(p._1).map(a => {
            val merged = a.balances.toSeq ++ p._2.toSeq
            val balances = merged.groupBy(_._1)
              .map(p => (p._1, Fixed8.sum(p._2.map(_._2).sum)))
              .filter(_._2.value > 0)
            new Account(a.active, a.name, balances, a.nextNonce + nonceIncrease.get(p._1).getOrElse(0L), a.version)
          }).getOrElse(new Account(true, "", p._2.filter(_._2.value > 0).toMap, 0))
          accountStore.set(p._1, account, batch)
        })
        // TODO accounts.foreach()
      })
      //latestHeader = block.header
      true
    } catch {
      case e: Throwable => {
        log.error("applyBlockToDB failed", e)
        false
      }
    }
  }

  private def verifyBlock(block: Block): Boolean = {
    if (!verifyHeader(block.header))
      false
    else if (!block.transactions.forall(verifyTransaction))
      false
    else if (!verifyRegisterNames(block.transactions))
      false
    else
      true
  }

  private def verifyTransaction(tx: Transaction): Boolean = {
    def checkAmount(): Boolean = {
      // TODO
      true
    }

    if (tx.txType == TransactionType.Miner) {
      // TODO check miner and only one miner tx
      true
    }
    else {
      var isValid = tx.verifySignature()
      // More TODO
      isValid && checkAmount()
    }
  }

  private def verifyHeader(header: BlockHeader): Boolean = {
    //    if (header.index != latestHeader.index + 1)
    //      false
    //    else if (header.timeStamp < latestHeader.timeStamp)
    //      false
    //    // TODO: verify rule of timeStamp and producer
    //    else if (header.id.equals(latestHeader.id))
    //      false
    //    else if (!header.prevBlock.equals(latestHeader.id))
    //      false
    //    else if (!header.verifySig())
    //      false
    //    else
    //      true
    header.verifySig()
  }

  private def verifyRegisterNames(transactions: Seq[Transaction]): Boolean = {
    var isValid = true
    val newNames = Set.empty[String]
    val registers = Set.empty[UInt160]
    transactions.foreach(tx => {
      if (tx.txType == TransactionType.RegisterName) {
        val name = new String(tx.data, "UTF-8")
        if (name.length != 10) // TODO: read "10" from config file
          isValid = false
        if (newNames.contains(name))
          isValid = false
        if (registers.contains(tx.fromPubKeyHash()))
          isValid = false
        newNames.add(name)
        registers.add(tx.fromPubKeyHash())
      }
    })
    // make sure name is not used
    //    newNames.foreach(name => {
    ////      if (nameToAccountStore.get(name) != None)
    ////        isValid = false
    ////    })
    ////
    isValid = !newNames.exists(nameExists)

    // make sure register never registed before
    //    registers.foreach(register => {
    //      val account = accountStore.get(register)
    //      if (account != None && account.get.name != "") {
    //        isValid = false
    //      }
    //    })
    isValid = !registers.exists(registerExists)

    isValid
  }
}
