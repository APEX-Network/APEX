/*
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: Crypto.scala
 *
 * @author: shan.huang@chinapex.com: 18-7-18 下午4:06@version: 1.0
 */

package com.apex.crypto

import java.security.{MessageDigest, SecureRandom}
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import org.bouncycastle.crypto.digests.{KeccakDigest, RIPEMD160Digest, SHA3Digest}


object Crypto {

  def randomBytes(num: Int): Array[Byte] = {
    val bytes = new Array[Byte](num)
    new SecureRandom().nextBytes(bytes)
    bytes
  }

  def hash256(data: Array[Byte]): Array[Byte] = {
     sha256(sha256(data))
  }

  def hash160(data: Array[Byte]): Array[Byte] = {
     RIPEMD160(sha256(data))
  }

  def RIPEMD160(data: Array[Byte]): Array[Byte] = {
     val messageDigest = new RIPEMD160Digest()
     messageDigest.update(data, 0, data.length)
     val out = Array.fill[Byte](messageDigest.getDigestSize())(0)
     messageDigest.doFinal(out, 0)
     out
  }

  def sha256(data: Array[Byte]): Array[Byte] = {
     MessageDigest.getInstance("SHA-256").digest(data)
  }

  def keccak256(data: Array[Byte]): Array[Byte] = {
    val messageDigest = new KeccakDigest(256)
    messageDigest.update(data, 0, data.length)
    val out = Array.fill[Byte](messageDigest.getDigestSize())(0)
    messageDigest.doFinal(out, 0)
    out
  }

  def sha3(data: Array[Byte]): Array[Byte] = {
    // the sha3 used in ethereum is actually keccak256
    keccak256(data)
  }

  def sha3_standard(data: Array[Byte]): Array[Byte] = {
    val messageDigest = new SHA3Digest(256)
    messageDigest.update(data, 0, data.length)
    val out = Array.fill[Byte](messageDigest.getDigestSize())(0)
    messageDigest.doFinal(out, 0)
    out
  }

//  def sha3omit12(data: Array[Byte]): Array[Byte] = {
//    // the sha3 used in ethereum is actually keccak256
//    val hash = keccak256(data)
//    Array.copyo
//  }

  def sign(message: Array[Byte], privateKey: Array[Byte]): Array[Byte] = {
     Ecdsa.encodeSignature(Ecdsa.sign(sha256(message), Ecdsa.PrivateKey(BinaryData(privateKey))))
  }

  def sign(message: Array[Byte], privateKey: Ecdsa.PrivateKey): Array[Byte] = {
     Ecdsa.encodeSignature(Ecdsa.sign(sha256(message), privateKey))
  }

  def verifySignature(message: Array[Byte], signature: Array[Byte], pubKey: Array[Byte]): Boolean = {
     val publicKey = Ecdsa.PublicKey(BinaryData(pubKey))

     Ecdsa.verifySignature(sha256(message), signature, publicKey)
  }

  def AesEncrypt(data: Array[Byte], key: Array[Byte], iv: Array[Byte]): Array[Byte] = {
    val secretKeySpec = new SecretKeySpec(key, "AES")
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new IvParameterSpec(iv))
    cipher.doFinal(data)
  }

  def AesDecrypt(data: Array[Byte], key: Array[Byte], iv: Array[Byte]): Array[Byte] = {
    val secretKeySpec = new SecretKeySpec(key, "AES")
    val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(iv))
    cipher.doFinal(data)
  }

  def calcNewAddr(addr: Array[Byte], nonce: Array[Byte]): UInt160 = {
    require(addr.length == 20)
    UInt160.fromBytes(hash160(addr ++ nonce))
  }

  def calcNewAddr(addr: UInt160, nonce: Array[Byte]): UInt160 = {
    calcNewAddr(addr.data, nonce)
  }
}

