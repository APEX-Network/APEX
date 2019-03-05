/*
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: TransactionReceiptTest.scala
 *
 * @author: shan.huang@chinapex.com: 2018-09-03 下午4:06@version: 1.0
 */

package com.apex.test

import com.apex.core.{Transaction, TransactionReceipt, TransactionType}
import com.apex.crypto.{BinaryData, Ecdsa, FixedNumber, UInt160, UInt256}
import org.junit.Test

@Test
class TransactionReceiptTest {
  @Test
  def testSerialize = {

    var privKey = new Ecdsa.PrivateKey(BinaryData("d39d51a8d40336b0c73af180308fe0e4ee357e45a59e8afeebf6895ddf78aa2f"))

    val tr = new TransactionReceipt(UInt256.Zero,
                    TransactionType.Transfer,
                    UInt160.Zero,
                    UInt160.Zero,
                    890,
                    123,
                    BinaryData("1234"),
                    789,
                    "")

    val o = new SerializerHelper[TransactionReceipt](
      TransactionReceipt.deserialize,
      (x, _) => x.txId == tr.txId
          && x.txType == tr.txType
          && x.from == tr.from
          && x.to == tr.to
          && x.blockIndex == tr.blockIndex
          && x.gasUsed == tr.gasUsed
          //&& x.totalGasUsed == tr.totalGasUsed
          && x.output.sameElements(tr.output)
          && x.status == tr.status
          && x.error == tr.error)
    o.test(tr)
  }
  @Test
  def testSerialize2 = {

    var privKey = new Ecdsa.PrivateKey(BinaryData("d39d51a8d40336b0c73af180308fe0e4ee357e45a59e8afeebf6895ddf78aa2f"))

    val tr = new TransactionReceipt(UInt256.Zero,
      TransactionType.Transfer,
      UInt160.Zero,
      UInt160.Zero,
      579,
      123,
      BinaryData("1234"),
      789,
      "ferdd rrjjj")

    val o = new SerializerHelper[TransactionReceipt](
      TransactionReceipt.deserialize,
      (x, _) => x.txId == tr.txId
        && x.txType == tr.txType
        && x.from == tr.from
        && x.to == tr.to
        && x.blockIndex == tr.blockIndex
        && x.gasUsed == tr.gasUsed
        //&& x.totalGasUsed == tr.totalGasUsed
        && x.output.sameElements(tr.output)
        && x.status == tr.status
        && x.error == tr.error
        && x.error.length == tr.error.length)
    o.test(tr)
  }
}
