/*
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: Crypto.scala
 *
 * @author: shan.huang@chinapex.com: 18-7-18 下午4:06@version: 1.0
 */

package com.apex.crypto

import java.security.{MessageDigest, Security, SecureRandom}
import javax.crypto.Cipher
import javax.crypto.spec.{SecretKeySpec, IvParameterSpec}
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.jce.provider.BouncyCastleProvider


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

  def sign(message: Array[Byte], privateKey: Array[Byte]): Array[Byte] = {
     val sig: BinaryData = Ecdsa.encodeSignature(Ecdsa.sign(Ecdsa.sha256(BinaryData(message)), Ecdsa.PrivateKey(BinaryData(privateKey))))
     sig
  }

  def sign(message: Array[Byte], privateKey: Ecdsa.PrivateKey): Array[Byte] = {
    val sig: BinaryData = Ecdsa.encodeSignature(Ecdsa.sign(Ecdsa.sha256(BinaryData(message)), privateKey))
    sig
  }

  def verifySignature(message: Array[Byte], signature: Array[Byte], pubKey: Array[Byte]): Boolean = {
     val publicKey = Ecdsa.PublicKey(BinaryData(pubKey))

     Ecdsa.verifySignature(Ecdsa.sha256(BinaryData(message)), BinaryData(signature), publicKey)
  }

  def AesEncrypt(data: Array[Byte], key: Array[Byte], iv: Array[Byte]): Array[Byte] = {
     Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())
     val secretKeySpec = new SecretKeySpec(key, "AES")
     val aes = Cipher.getInstance("AES/CBC/PKCS5PADDING", BouncyCastleProvider.PROVIDER_NAME)
     aes.init(Cipher.ENCRYPT_MODE, secretKeySpec, new IvParameterSpec(iv))
     aes.doFinal(data)
  }

  def AesDecrypt(data: Array[Byte], key: Array[Byte], iv: Array[Byte]): Array[Byte] = {
     Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())
     val secretKeySpec = new SecretKeySpec(key, "AES")
     val aes = Cipher.getInstance("AES/CBC/PKCS5PADDING", BouncyCastleProvider.PROVIDER_NAME)
     aes.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(iv))
     aes.doFinal(data)
  }
}

