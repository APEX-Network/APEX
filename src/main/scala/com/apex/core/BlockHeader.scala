package com.apex.core

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

import com.apex.crypto.Ecdsa.{PrivateKey, PublicKey}
import com.apex.crypto.{BinaryData, Crypto, UInt160, UInt256}
import com.apex.vm.DataWord
import play.api.libs.json.{JsValue, Json, Writes}

class BlockHeader(val index: Long,
                  val timeStamp: Long,
                  val merkleRoot: UInt256,
                  val prevBlock: UInt256,
                  val producer: UInt160,
                  var producerSig: BinaryData,
                  val version: Int = 0x01) extends Identifier[UInt256] {

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case that: BlockHeader => id.equals(that.id)
      case _ => false
    }
  }

  def timeString(): String = {
    val zonedDateTimeUtc = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeStamp), ZoneId.of("UTC"))
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss,SSS") // DateTimeFormatter.ISO_OFFSET_DATE_TIME
    dateTimeFormatter.format(zonedDateTimeUtc)
  }

  def shortId(): String = {
    id.toString.substring(0, 8)
  }

  override def hashCode(): Int = {
    id.hashCode()
  }

  override protected def genId(): UInt256 = {
    val bs = new ByteArrayOutputStream()
    val os = new DataOutputStream(bs)
    serialize(os)
    UInt256.fromBytes(Crypto.hash256(bs.toByteArray))
  }

  private def serializeForSign(os: DataOutputStream) = {
    import com.apex.common.Serializable._
    os.writeInt(version)
    os.writeLong(index)
    os.writeLong(timeStamp)
    os.write(merkleRoot)
    os.write(prevBlock)
    os.write(producer)
    // skip the producerSig
  }

  override def serialize(os: DataOutputStream): Unit = {
    import com.apex.common.Serializable._
    serializeForSign(os)
    os.writeByteArray(producerSig)
  }

  private def getSigTargetData(): Array[Byte] = {
    val bs = new ByteArrayOutputStream()
    val os = new DataOutputStream(bs)
    serializeForSign(os)
    bs.toByteArray
  }

  def sign(privKey: PrivateKey) = {
    producerSig = Crypto.sign(getSigTargetData, privKey)
  }

  def verifySig(): Boolean = {
    Crypto.verifySignature(getSigTargetData(), producerSig, producer)
  }
}

object BlockHeader {
  implicit val blockHeaderWrites = new Writes[BlockHeader] {
    override def writes(o: BlockHeader): JsValue = Json.obj(
      "hash" -> o.id.toString,
      "index" -> o.index,
      "timeStamp" -> o.timeStamp,
      "time" -> o.timeString(),
      "merkleRoot" -> o.merkleRoot.toString,
      "prevBlock" -> o.prevBlock.toString,
      "producer" -> o.producer.address,
      "producerSig" -> o.producerSig.toString,
      "version" -> o.version
    )
  }

  def build(index: Long, timeStamp: Long, merkleRoot: UInt256, prevBlock: UInt256,
            privateKey: PrivateKey): BlockHeader = {

    val header = new BlockHeader(index, timeStamp, merkleRoot, prevBlock,
      privateKey.publicKey.pubKeyHash, BinaryData.empty)
    header.sign(privateKey)
    header
  }

  def deserialize(is: DataInputStream): BlockHeader = {
    import com.apex.common.Serializable._
    val version = is.readInt
    new BlockHeader(
      index = is.readLong,
      timeStamp = is.readLong,
      merkleRoot = is.readObj(UInt256.deserialize),
      prevBlock = is.readObj(UInt256.deserialize),
      producer = is.readObj(UInt160.deserialize),
      producerSig = is.readByteArray,
      version = version
    )
  }

  def fromBytes(data: Array[Byte]): BlockHeader = {
    val bs = new ByteArrayInputStream(data)
    val is = new DataInputStream(bs)
    deserialize(is)
  }
}
