package com.apex.core

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

import com.apex.common.Serializable
import com.apex.crypto.{BinaryData, Crypto, Ecdsa, Fixed8, UInt160, UInt256}
import play.api.libs.json.{JsValue, Json, Writes}

class Transaction(val txType: TransactionType.Value,
                           val from: Ecdsa.PublicKey,   // 33 bytes pub key
                           val toPubKeyHash: UInt160,
                           val toName: String,
                           val amount: Fixed8,
                           val assetId: UInt256,
                           val nonce: Long,
                           val data: BinaryData,
                           val gasPrice: BinaryData,
                           val gasLimit: BinaryData,
                           var signature: BinaryData,
                           val version: Int = 0x01) extends Identifier[UInt256] with Serializable {

  //TODO: read settings
  def fee: Fixed8 = Fixed8.Zero

  def fromPubKeyHash() : UInt160 = {
    from.pubKeyHash
  }

  def fromAddress(): String = {
    from.address
  }

  def toAddress(): String = {
    Ecdsa.PublicKeyHash.toAddress(toPubKeyHash.data)
  }

  override protected def genId(): UInt256 = {
    val bs = new ByteArrayOutputStream()
    val os = new DataOutputStream(bs)
    serialize(os)
    UInt256.fromBytes(Crypto.hash256(bs.toByteArray))
  }

  override def serialize(os: DataOutputStream): Unit = {
    import com.apex.common.Serializable._

    serializeForSign(os)

    os.writeByteArray(signature)
  }

  def serializeForSign(os: DataOutputStream) = {
    import com.apex.common.Serializable._
    os.writeByte(txType.toByte)
    os.writeInt(version)
    os.write(from)
    os.write(toPubKeyHash)
    os.writeString(toName)
    os.write(amount)
    os.write(assetId)
    os.writeLong(nonce)
    os.writeByteArray(data)
    os.writeByteArray(gasPrice)
    os.writeByteArray(gasLimit)

    // skip signature

  }

  def dataForSigning(): Array[Byte] = {
    val bs = new ByteArrayOutputStream()
    val os = new DataOutputStream(bs)
    serializeForSign(os)
    bs.toByteArray
  }

  def sign(privateKey: Ecdsa.PrivateKey) = {
    signature = Crypto.sign(dataForSigning(), privateKey.toBin)
  }

  def verifySignature(): Boolean = {
    Crypto.verifySignature(dataForSigning(), signature, from.toBin)
  }

}

object Transaction {
  implicit val transactionWrites = new Writes[Transaction] {
    override def writes(o: Transaction): JsValue = {
      Json.obj(
            "id" -> o.id.toString,
            "type" -> o.txType.toString,
            "from" -> { if (o.txType == TransactionType.Miner) "" else o.fromAddress },
            "to" ->  o.toAddress,
            "toName" -> o.toName,
            "amount" -> o.amount.toString,
            "assetId" -> o.assetId.toString,
            "nonce" -> o.nonce.toString,
            "data" -> o.data.toString,
            "gasPrice" -> o.gasPrice.toString,
            "gasLimit" -> o.gasLimit.toString,
            "signature" -> o.signature.toString,
            "version" -> o.version
          )
    }
  }

  def deserialize(is: DataInputStream): Transaction = {
    import com.apex.common.Serializable._

    val txType = TransactionType(is.readByte)
    val version = is.readInt
    val from = Ecdsa.PublicKey.deserialize(is)
    val toPubKeyHash = UInt160.deserialize(is)
    val toName = is.readString
    val amount = Fixed8.deserialize(is)
    val assetId = UInt256.deserialize(is)
    val nonce = is.readLong
    val data = is.readByteArray
    val gasPrice = is.readByteArray
    val gasLimit = is.readByteArray
    val signature = is.readByteArray

    new Transaction(txType, from, toPubKeyHash, toName, amount, assetId, nonce, data, gasPrice, gasLimit, signature, version)
  }
}