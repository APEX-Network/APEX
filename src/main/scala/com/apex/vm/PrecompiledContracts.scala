/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: PrecompiledContracts.scala
 *
 * @author: ruixiao.xiao@chinapex.com: 18-11-30 下午1:33@version: 1.0
 *
 */

package com.apex.vm

import com.apex.crypto.Crypto
import com.apex.settings.ContractSettings

object PrecompiledContracts {
  private val ecRecover = new ECRecover
  private val sha256 = new Sha256
  private val ripempd160 = new Ripempd160
  private val identity = new Identity
  private val modExp = new ModExp
  private val altBN128Add = new BN128Addition
  private val altBN128Mul = new BN128Multiplication
  private val altBN128Pairing = new BN128Pairing

  private val ecRecoverAddr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000001")
  private val sha256Addr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000002")
  private val ripempd160Addr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000003")
  private val identityAddr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000004")
  private val modExpAddr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000005")
  private val altBN128AddAddr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000006")
  private val altBN128MulAddr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000007")
  private val altBN128PairingAddr = DataWord.of("0000000000000000000000000000000000000000000000000000000000000008")

  def getContractForAddress(address: DataWord, settings: ContractSettings): PrecompiledContract = {
    if (address == null) identity
    else if (address == ecRecoverAddr) ecRecover
    else if (address == sha256Addr) sha256
    else if (address == ripempd160Addr) ripempd160
    else if (address == identityAddr) identity
    // Byzantium precompiles
    else if (address == modExpAddr && settings.eip198) modExp
    else if (address == altBN128AddAddr && settings.eip213) altBN128Add
    else if (address == altBN128MulAddr && settings.eip213) altBN128Mul
    else if (address == altBN128PairingAddr && settings.eip212) altBN128Pairing
    else null
  }
}

object PrecompiledContract {
  def addSafely(a: Int, b: Int): Int = {
    val res = a.toLong + b.toLong
    if (res > Integer.MAX_VALUE) Integer.MAX_VALUE
    else res.toInt
  }
}

trait PrecompiledContract {
  def getGasForData(data: Array[Byte]): Long

  def execute(data: Array[Byte]): (Boolean, Array[Byte])
}

class Identity extends PrecompiledContract {
  override def getGasForData(data: Array[Byte]): Long = {
    // gas charge for the execution:
    // minimum 1 and additional 1 for each 32 bytes word (round  up)
    if (data == null) {
      15
    } else {
      15 + VM.getSizeInWords(data.length) * 3
    }
  }

  override def execute(data: Array[Byte]): (Boolean, Array[Byte]) = {
    (true, data)
  }
}

class Sha256 extends PrecompiledContract {
  override def getGasForData(data: Array[Byte]): Long = {
    // gas charge for the execution:
    // minimum 50 and additional 50 for each 32 bytes word (round  up)
    if (data == null) {
      60
    } else {
      60 + VM.getSizeInWords(data.length) * 12
    }
  }

  override def execute(data: Array[Byte]): (Boolean, Array[Byte]) = {
    if (data == null) {
      (true, Crypto.sha256(new Array[Byte](0)))
    } else {
      (true, Crypto.sha256(data))
    }
  }
}

class Ripempd160 extends PrecompiledContract {
  override def getGasForData(data: Array[Byte]): Long = {
    // TODO #POC9 Replace magic numbers with constants
    // gas charge for the execution:
    // minimum 50 and additional 50 for each 32 bytes word (round  up)
    if (data == null) {
      600
    } else {
      600 + VM.getSizeInWords(data.length) * 120
    }
  }

  override def execute(data: Array[Byte]): (Boolean, Array[Byte]) = {
    val result = Crypto.RIPEMD160(if (data == null) Array.empty else data)
    (true, DataWord.of(result).getData)
  }
}

class ECRecover extends PrecompiledContract {
  override def getGasForData(data: Array[Byte]): Long = 3000

  override def execute(data: Array[Byte]): (Boolean, Array[Byte]) = {
    val h = new Array[Byte](32)
    val v = new Array[Byte](32)
    val r = new Array[Byte](32)
    val s = new Array[Byte](32)

    var out: DataWord = null
    try {
      System.arraycopy(data, 0, h, 0, 32)
      System.arraycopy(data, 32, v, 0, 32)
      System.arraycopy(data, 64, r, 0, 32)
      //      val sLength = if (data.length < 128) data.length - 96 else 32
      //      System.arraycopy(data, 96, s, 0, sLength)
      //      val signature = ECKey.ECDSASignature.fromComponents(r, s, v(31))
      //      if (validateV(v) && signature.validateComponents) {
      //        out = DataWord.of(ECKey.signatureToAddress(h, signature))
      //      }
    } catch {
      case _: Throwable => {}
    }
    if (out == null) {
      (true, Array.empty)
    } else {
      (true, out.getData)
    }
  }

  private def validateV(v: Array[Byte]): Boolean = {
    var j = 0
    for (i <- 0 to v.length if v(i) == 0) {
      j += 1
    }
    j == v.length
  }
}


/**
  * Computes modular exponentiation on big numbers
  *
  * format of data[] array:
  * [length_of_BASE] [length_of_EXPONENT] [length_of_MODULUS] [BASE] [EXPONENT] [MODULUS]
  * where every length is a 32-byte left-padded integer representing the number of bytes.
  * Call data is assumed to be infinitely right-padded with zero bytes.
  *
  * Returns an output as a byte array with the same length as the modulus
  */
class ModExp extends PrecompiledContract {
  private val GQUAD_DIVISOR = BigInt(20)

  private val ARGS_OFFSET = 32 * 3 // addresses length part

  import PrecompiledContract._

  override def getGasForData(inData: Array[Byte]): Long = {
    val data = if (inData == null) Array.emptyByteArray else inData
    val baseLen = parseLen(data, 0)
    val expLen = parseLen(data, 1)
    val modLen = parseLen(data, 2)
    val expHighBytes = data.parseBytes(addSafely(ARGS_OFFSET, baseLen), math.min(expLen, 32))
    val multComplexity = getMultComplexity(Math.max(baseLen, modLen))
    val adjExpLen = getAdjustedExponentLength(expHighBytes, expLen)
    // use big numbers to stay safe in case of overflow
    val gas = BigInt(multComplexity) * BigInt(Math.max(adjExpLen, 1)) / GQUAD_DIVISOR
    if (gas < BigInt(Long.MaxValue)) gas.longValue else Long.MaxValue
  }

  override def execute(data: Array[Byte]): (Boolean, Array[Byte]) = {
    if (data == null) {
      (true, Array.emptyByteArray)
    } else {
      val baseLen = parseLen(data, 0)
      val expLen = parseLen(data, 1)
      val modLen = parseLen(data, 2)

      val base = parseArg(data, ARGS_OFFSET, baseLen)
      val exp = parseArg(data, addSafely(ARGS_OFFSET, baseLen), expLen)
      val mod = parseArg(data, addSafely(addSafely(ARGS_OFFSET, baseLen), expLen), modLen)
      if (mod == 0) {
        (true, Array.emptyByteArray)
      } else {
        val res = base.modPow(exp, mod).toByteArray.stripLeadingZeroes
        // adjust result to the same length as the modulus has
        if (res.length < modLen) {
          val adjRes = new Array[Byte](modLen)
          System.arraycopy(res, 0, adjRes, modLen - res.length, res.length)
          (true, adjRes)
        } else {
          (true, res)
        }
      }
    }
  }

  private def getMultComplexity(x: Long): Long = {
    val x2 = x * x
    if (x <= 64) {
      x2
    } else if (x <= 1024) {
      x2 / 4 + 96 * x - 3072
    } else {
      x2 / 16 + 480 * x - 199680
    }
  }

  private def getAdjustedExponentLength(expHighBytes: Array[Byte], expLen: Long): Long = {
    val leadingZeros = expHighBytes.numberOfLeadingZeros
    var highestBit = 8 * expHighBytes.length - leadingZeros
    // set index basement to zero
    if (highestBit > 0) {
      highestBit -= 1
    }
    if (expLen <= 32) {
      highestBit
    } else {
      8 * (expLen - 32) + highestBit
    }
  }

  private def parseLen(data: Array[Byte], idx: Int) = {
    val bytes = data.parseBytes(32 * idx, 32)
    DataWord.of(bytes).intValueSafe
  }

  private def parseArg(data: Array[Byte], offset: Int, len: Int) = {
    val bytes = data.parseBytes(offset, len)
    bytes.toBigInt
  }
}

/**
  * Computes point addition on Barreto–Naehrig curve.
  * See {@link BN128Fp} for details<br/>
  * <br/>
  *
  * input data[]:<br/>
  * two points encoded as (x, y), where x and y are 32-byte left-padded integers,<br/>
  * if input is shorter than expected, it's assumed to be right-padded with zero bytes<br/>
  * <br/>
  *
  * output:<br/>
  * resulting point (x', y'), where x and y encoded as 32-byte left-padded integers<br/>
  *
  */
class BN128Addition extends PrecompiledContract {
  override def getGasForData(data: Array[Byte]): Long = 500

  override def execute(in: Array[Byte]): (Boolean, Array[Byte]) = {
    val data = if (in == null) Array.emptyByteArray else in
    val x1 = data.parseWord(0)
    val y1 = data.parseWord(1)
    val x2 = data.parseWord(2)
    val y2 = data.parseWord(3)
    throw new NotImplementedError
  }
}

/**
  * Computes multiplication of scalar value on a point belonging to Barreto–Naehrig curve.
  * See {@link BN128Fp} for details<br/>
  * <br/>
  *
  * input data[]:<br/>
  * point encoded as (x, y) is followed by scalar s, where x, y and s are 32-byte left-padded integers,<br/>
  * if input is shorter than expected, it's assumed to be right-padded with zero bytes<br/>
  * <br/>
  *
  * output:<br/>
  * resulting point (x', y'), where x and y encoded as 32-byte left-padded integers<br/>
  *
  */
class BN128Multiplication extends PrecompiledContract {
  override def getGasForData(data: Array[Byte]) = 40000

  override def execute(data: Array[Byte]) = throw new NotImplementedError
}

/**
  * Computes pairing check. <br/>
  * See {@link PairingCheck} for details.<br/>
  * <br/>
  *
  * Input data[]: <br/>
  * an array of points (a1, b1, ... , ak, bk), <br/>
  * where "ai" is a point of {@link BN128Fp} curve and encoded as two 32-byte left-padded integers (x; y) <br/>
  * "bi" is a point of {@link BN128G2} curve and encoded as four 32-byte left-padded integers {@code (ai + b; ci + d)},
  * each coordinate of the point is a big-endian {@link Fp2} number, so {@code b} precedes {@code a} in the encoding:
  * {@code (b, a; d, c)} <br/>
  * thus each pair (ai, bi) has 192 bytes length, if 192 is not a multiple of {@code data.length} then execution fails <br/>
  * the number of pairs is derived from input length by dividing it by 192 (the length of a pair) <br/>
  * <br/>
  *
  * output: <br/>
  * pairing product which is either 0 or 1, encoded as 32-byte left-padded integer <br/>
  *
  */
class BN128Pairing extends PrecompiledContract {
  private val PAIR_SIZE = 192

  override def getGasForData(data: Array[Byte]): Long = if (data == null) 100000 else 80000 * (data.length / PAIR_SIZE) + 100000

  override def execute(data: Array[Byte]): (Boolean, Array[Byte]) = {
    throw new NotImplementedError
  }
}