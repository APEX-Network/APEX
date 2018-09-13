/*
 *
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: DataStoreTest.scala
 *
 * @author: ruixiao.xiao@chinapex.com: 18-9-13 下午7:29@version: 1.0
 *
 */

package com.apex.test

import java.time.Instant

import akka.http.scaladsl.model.DateTime
import com.apex.core.{BlockHeader, HeaderStore}
import com.apex.crypto.BinaryData
import com.apex.crypto.Ecdsa.{PrivateKey, PublicKey}
import com.apex.storage.LevelDbStorage
import org.junit.Test

import scala.reflect.io.Directory

@Test
class DataStoreTest {

  @Test
  def testCommitRollBack: Unit = {
    val db = LevelDbStorage.open("test_RollBack")
    val store = new HeaderStore(db, 10)
    val blk1 = createBlockHeader
    println(blk1.id)
    store.beginTransaction()
    store.set(blk1.id, blk1)
    assert(store.get(blk1.id).get.equals(blk1))
    store.rollBack()
    assert(store.get(blk1.id).isEmpty)
    val blk2 = createBlockHeader
    println(blk2.id)
    store.set(blk2.id, blk2)
    store.commit()
    assert(store.get(blk2.id).get.equals(blk2))
    store.beginTransaction()
    val blk3 = createBlockHeader
    println(blk3.id)
    store.set(blk3.id, blk3)
    assert(store.get(blk3.id).get.equals(blk3))
    store.rollBack()
    assert(store.get(blk2.id).get.equals(blk2))
    assert(store.get(blk3.id).isEmpty)
    db.close()
    Directory("test_RollBack").deleteRecursively()
  }

  private def createBlockHeader() = {
    val prevBlock = SerializerTest.testHash256("prev")
    val merkleRoot = SerializerTest.testHash256("root")
    val producer = PublicKey("03b4534b44d1da47e4b4a504a210401a583f860468dec766f507251a057594e682") // TODO: read from settings
    val producerPrivKey = new PrivateKey(BinaryData("7a93d447bffe6d89e690f529a3a0bdff8ff6169172458e04849ef1d4eafd7f86"))
    val timeStamp = Instant.now.toEpochMilli
    new BlockHeader(0, timeStamp, merkleRoot, prevBlock, producer, BinaryData("0000"))
  }
}
