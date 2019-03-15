/*
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: Fixed8.scala
 *
 * @author: ruixiao.xiao@chinapex.com: 18-7-18 下午4:06@version: 1.0
 */

package com.apex.crypto

import java.io.{DataInputStream, DataOutputStream}

import com.apex.common.Serializable

case class FixedNumber(value: BigInt = 0) extends Serializable {
  def isZero: Boolean = value == 0

  override def toString: String = {
    val v1 = BigDecimal(value)./(BigDecimal(FixedNumber.One.value))
    v1.bigDecimal.stripTrailingZeros.toPlainString()
  }

  def equals(that: FixedNumber): Boolean = this == that

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case that: FixedNumber => equals(that)
      case _ => false
    }
  }

  override def serialize(os: DataOutputStream): Unit = {
    import com.apex.common.Serializable._
    os.writeByteArray(value.toByteArray)
  }

  def unary_-(): FixedNumber = FixedNumber(-value)

  def +(that: FixedNumber): FixedNumber = FixedNumber(value + that.value)

  def -(that: FixedNumber): FixedNumber = FixedNumber(value - that.value)

  def *(that: FixedNumber): FixedNumber = FixedNumber(value * that.value)

  def *(that: BigInt): FixedNumber = FixedNumber(value * that)

  def >(that: FixedNumber): Boolean = value > that.value

  def >(that: BigInt): Boolean = value > that

  def >=(that: FixedNumber): Boolean = value >= that.value

  def ==(that: FixedNumber): Boolean = {
    that match {
      case null => false
      case _ => value == that.value
    }
  }
}

object FixedNumber {
  //final val MaxValue: Fixed18 = new Fixed18(Long.MaxValue)
  final val MinValue: FixedNumber = FixedNumber(1)
  final val One: FixedNumber = FixedNumber(1000000000000000000L) // 10^18
  final val Ten: FixedNumber = FixedNumber(One.value * 10)
  final val Zero: FixedNumber = FixedNumber(0)

  implicit val serializer: DataInputStream => FixedNumber = deserialize

  def deserialize(is: DataInputStream): FixedNumber = {
    import com.apex.common.Serializable._
    FixedNumber(BigInt(is.readByteArray()))
  }

  /**
    *  from raw value CPX, 1 means one token, this is not same to FixedNumber(1)
    * @param d
    * @return
    */
  def fromDecimal(d: BigDecimal): FixedNumber = {
    val value = d * BigDecimal(One.value)
    FixedNumber(value.toBigInt())
  }

  implicit object Fixed8Numeric extends Numeric[FixedNumber] {
    override def plus(x: FixedNumber, y: FixedNumber): FixedNumber = x + y

    override def minus(x: FixedNumber, y: FixedNumber): FixedNumber = x - y

    override def times(x: FixedNumber, y: FixedNumber): FixedNumber = x * y

    override def negate(x: FixedNumber): FixedNumber = -x

    override def fromInt(x: Int): FixedNumber = FixedNumber(x)

    override def toInt(x: FixedNumber): Int = x.value.toInt

    override def toLong(x: FixedNumber): Long = x.value.toLong

    override def toFloat(x: FixedNumber): Float = x.value.toFloat

    override def toDouble(x: FixedNumber): Double = x.value.toDouble

    override def compare(x: FixedNumber, y: FixedNumber): Int = {
      if (x == null || y == null) throw new IllegalArgumentException
      x.value.compareTo(y.value)
    }
  }

}

