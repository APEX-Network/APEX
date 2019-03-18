/*
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: WitnessList.scala
 *
 * @author: shan.huang@chinapex.com: 18-7-18 下午4:06@version: 1.0
 */

package com.apex.consensus

import java.io.{DataInputStream, DataOutputStream}

import com.apex.common.ApexLogging
import com.apex.crypto.{UInt160, UInt256}
import play.api.libs.json.{JsValue, Json, Writes}

import scala.collection.mutable

class WitnessList(val witnesses: Array[WitnessInfo],  // sorted by Location
                  val generateInBlock: UInt256,
                  val version: Int = 0x01) extends com.apex.common.Serializable with ApexLogging {

  private val addrList = witnesses.map(_.addr).toSet

  override def serialize(os: DataOutputStream): Unit = {
    import com.apex.common.Serializable._

    os.writeInt(version)
    os.writeSeq(witnesses)
    os.write(generateInBlock)
  }

  def contains(witness: UInt160): Boolean = {
    addrList.contains(witness)
  }

  def logInfo(name: String) = {
    log.info(s"$name:")
    witnesses.foreach(w =>
      log.info(s"  ${w.addr.address.substring(0, 7)}  ${w.voteCounts}  ${w.longitude}  ${w.latitude} "))
  }
}

object WitnessList {

  def create(witnesses: Array[WitnessInfo],
             generateInBlock: UInt256,
             version: Int = 0x01): WitnessList = {
    new WitnessList(sortByLocation(witnesses), generateInBlock, version)
  }

  def sortByVote(witnesses: Array[WitnessInfo]): Array[WitnessInfo] = {
    witnesses.sortWith((w1, w2) => {
      require(!w1.addr.equals(w2.addr))
      if (w1.voteCounts.value == w2.voteCounts.value)
        w1.addr.address > w2.addr.address
      else
        w1.voteCounts.value > w2.voteCounts.value
    })
  }

  def sortByLocation(witnesses: Array[WitnessInfo]): Array[WitnessInfo] = {
    witnesses.sortWith((w1, w2) => {
      require(!w1.addr.equals(w2.addr))
      if (w1.longitude == w2.longitude) {
        if (w1.latitude == w2.latitude)
          w1.addr > w2.addr
        else
          w1.latitude > w2.latitude
      }
      else
        w1.longitude > w2.longitude
    })
  }

  def sortByAddr(witnesses: Array[UInt160]): Array[UInt160] = {
    witnesses.sortWith((w1, w2) => {
      require(!w1.equals(w2))
      w1 > w2
    })
  }

  def getLeastVote(witnesses: Array[WitnessInfo]): WitnessInfo = {
    sortByVote(witnesses).last
  }

  def removeLeastVote(witnesses: Array[WitnessInfo]): mutable.Map[UInt160, WitnessInfo] = {
    val newWitnesses = mutable.Map.empty[UInt160, WitnessInfo]
    val sorted = sortByVote(witnesses)
    for (i <- 0 to sorted.size - 2) {
      val w = sorted(i)
      newWitnesses.update(w.addr, w)
    }
    newWitnesses
  }

  def deserialize(is: DataInputStream): WitnessList = {
    import com.apex.common.Serializable._

    val version = is.readInt()
    val witnesses = is.readSeq(WitnessInfo.deserialize)
    val generateInBlock = is.readObj(UInt256.deserialize)

    new WitnessList(witnesses.toArray, generateInBlock, version)
  }

  implicit val witnessListWrites = new Writes[WitnessList] {
    override def writes(o: WitnessList): JsValue = {
      Json.obj(
        "witnesses" -> o.witnesses,
        "generateInBlock" -> o.generateInBlock.toString,
        "version" -> o.version
      )
    }
  }

}
