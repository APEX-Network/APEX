/*
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: BlockHeaderTest.scala
 *
 * @author: ruixiao.xiao@chinapex.com: 18-7-18 下午6:50@version: 1.0
 */

package com.apex.test

import akka.http.scaladsl.model.DateTime
import com.apex.core.BlockHeader
import com.apex.crypto.BinaryData
import com.apex.crypto.Ecdsa.PrivateKey
import org.junit.Test
import play.api.libs.json.Json

@Test
class BlockHeaderTest {
  @Test
  def testSerialize = {
    val prevBlock = SerializerTest.testHash256("prev")
    val merkleRoot = SerializerTest.testHash256("root")
    val producer = BinaryData("03b4534b44d1da47e4b4a504a210401a583f860468dec766f507251a057594e682") // TODO: read from settings
    val producerPrivKey = new PrivateKey(BinaryData("7a93d447bffe6d89e690f529a3a0bdff8ff6169172458e04849ef1d4eafd7f86"))
    val timeStamp = DateTime.now.clicks
    val a = new BlockHeader(0, timeStamp, merkleRoot, prevBlock, producer, BinaryData("0000"))
    val o = new SerializerTest[BlockHeader](
      BlockHeader.deserialize,
      (x, _) => x.version.equals(a.version)
        && x.id.equals(a.id)
        && x.index.equals(a.index)
        && x.timeStamp.equals(a.timeStamp)
        && x.merkleRoot.equals(a.merkleRoot)
        && x.prevBlock.equals(a.prevBlock)
    )
    o.test(a)
  }

  @Test
  def testToJson = {
    val prevBlock = SerializerTest.testHash256("prev")
    val merkleRoot = SerializerTest.testHash256("root")
    val producer = BinaryData("03b4534b44d1da47e4b4a504a210401a583f860468dec766f507251a057594e682") // TODO: read from settings
    val producerPrivKey = new PrivateKey(BinaryData("7a93d447bffe6d89e690f529a3a0bdff8ff6169172458e04849ef1d4eafd7f86"))
    val timeStamp = DateTime.now.clicks
    val a = new BlockHeader(0, timeStamp, merkleRoot, prevBlock, producer, BinaryData("0000"))
    assert(Json.toJson(a).toString.equals(
      s"""{"id":"${a.id}","index":${a.index},"timeStamp":${a.timeStamp},"merkleRoot":"${a.merkleRoot}","prevBlock":"${a.prevBlock}","producer":"${a.producer}","producerSig":"${a.producerSig}","version":${a.version}}"""))
  }
}
