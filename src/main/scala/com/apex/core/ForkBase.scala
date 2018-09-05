/*
 *
 *
 *
 *
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: ForkBase.scala
 *
 * @author: ruixiao.xiao@chinapex.com: 18-8-28 上午10:53@version: 1.0
 */

package com.apex.core

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

import com.apex.crypto.Ecdsa.PublicKey
import com.apex.crypto.UInt256
import com.apex.exceptions.{InvalidOperationException, UnExpectedError}
import com.apex.settings.Witness
import com.apex.storage.LevelDbStorage

import collection.mutable.{ListBuffer, Map, Seq, SortedMap}
import scala.collection.mutable

class MultiMap[K, V] extends mutable.Iterable[(K, V)] {
  private val container = Map.empty[K, ListBuffer[V]]

  def contains(k: K) = {
    container.contains(k)
  }

  def get(k: K) = {
    container(k)
  }

  def put(k: K, v: V) = {
    if (!container.contains(k)) {
      container.put(k, ListBuffer.empty)
    }
    container(k).append(v)
  }

  def remove(k: K): Option[Seq[V]] = {
    container.remove(k)
  }

  override def head: (K, V) = iterator.next()

  override def iterator: Iterator[(K, V)] = new MultiMapIterator(container)

  class MultiMapIterator(container: Map[K, ListBuffer[V]]) extends Iterator[(K, V)] {
    private val it = container.iterator

    private var it2: Option[Iterator[V]] = None
    private var k: Option[K] = None

    override def hasNext: Boolean = {
      if (it2.isEmpty || !it2.get.hasNext) {
        nextIt
      }

      !it2.isEmpty && it2.get.hasNext
    }

    override def next(): (K, V) = {
      if (!hasNext) throw new NoSuchElementException
      (k.get, it2.get.next())
    }

    private def nextIt = {
      if (it.hasNext) {
        val next = it.next()
        it2 = Some(next._2.iterator)
        k = Some(next._1)
      }
    }
  }

}

object MultiMap {
  def empty[K, V] = new MultiMap[K, V]
}

class SortedMultiMap1[K, V](implicit ord: Ordering[K]) extends Iterable[(K, V)] {
  private val container = SortedMap.empty[K, ListBuffer[V]]

  def contains(k: K) = {
    container.contains(k)
  }

  def get(k: K) = {
    container(k)
  }

  def put(k: K, v: V): Unit = {
    if (!container.contains(k)) {
      container.put(k, ListBuffer.empty[V])
    }
    container(k).append(v)
  }

  def remove(k: K): Option[Seq[V]] = {
    container.remove(k)
  }

  override def head: (K, V) = iterator.next()

  override def iterator: Iterator[(K, V)] = {
    new SortedMultiMapIterator(container)
  }

  class SortedMultiMapIterator(val map: SortedMap[K, ListBuffer[V]]) extends Iterator[(K, V)] {
    private val it = map.iterator

    private var it2: Option[Iterator[V]] = None
    private var k: Option[K] = None

    override def hasNext: Boolean = {
      if (it2.isEmpty || !it2.get.hasNext) {
        nextIt
      }
      !it2.isEmpty && it2.get.hasNext
    }

    override def next(): (K, V) = {
      if (!hasNext) throw new NoSuchElementException
      (k.get, it2.get.next())
    }

    private def nextIt = {
      if (it.hasNext) {
        val next = it.next()
        it2 = Some(next._2.iterator)
        k = Some(next._1)
      }
    }
  }

}

class SortedMultiMap2[K1, K2, V](implicit ord1: Ordering[K1], ord2: Ordering[K2]) extends Iterable[(K1, K2, V)] {
  private val container = SortedMap.empty[K1, SortedMultiMap1[K2, V]]

  def contains(k1: K1, k2: K2) = {
    container.contains(k1) && container(k1).contains(k2)
  }

  def get(k1: K1, k2: K2) = {
    container(k1).get(k2)
  }

  def put(k1: K1, k2: K2, v: V): Unit = {
    if (!container.contains(k1)) {
      container.put(k1, SortedMultiMap.empty[K2, V])
    }
    container(k1).put(k2, v)
  }

  def remove(k1: K1, k2: K2): Option[Seq[V]] = {
    if (container.contains(k1)) {
      container(k1).remove(k2)
    } else {
      None
    }
  }

  override def head: (K1, K2, V) = iterator.next()

  override def iterator: Iterator[(K1, K2, V)] = {
    new SortedMultiMapIterator(container)
  }

  class SortedMultiMapIterator[K1, K2, V](val map: SortedMap[K1, SortedMultiMap1[K2, V]]) extends Iterator[(K1, K2, V)] {
    private val it = map.iterator

    private var it2: Option[Iterator[(K2, V)]] = None
    private var k1: Option[K1] = None

    override def hasNext: Boolean = {
      if (it2.isEmpty || !it2.get.hasNext) {
        nextIt
      }
      !it2.isEmpty && it2.get.hasNext
    }

    override def next(): (K1, K2, V) = {
      if (!hasNext) throw new NoSuchElementException
      val next = it2.get.next()
      (k1.get, next._1, next._2)
    }

    private def nextIt = {
      if (it.hasNext) {
        val next = it.next()
        it2 = Some(next._2.iterator)
        k1 = Some(next._1)
      }
    }
  }

}

object SortedMultiMap {
  def empty[A, B]()(implicit ord: Ordering[A]): SortedMultiMap1[A, B] = new SortedMultiMap1[A, B]

  def empty[A, B, C]()(implicit ord1: Ordering[A], ord2: Ordering[B]): SortedMultiMap2[A, B, C] = new SortedMultiMap2[A, B, C]
}

case class ForkItem(block: Block, lastProducerHeight: mutable.Map[PublicKey, Int], master: Boolean = true) {
  private var _confirmedHeight: Int = -1

  def confirmedHeight: Int = {
    if (_confirmedHeight == -1) {
      val index = lastProducerHeight.size * 2 / 3
      _confirmedHeight = lastProducerHeight.values.toSeq.reverse(index)
    }
    _confirmedHeight
  }

  def toBytes: Array[Byte] = {
    import com.apex.common.Serializable._
    val bs = new ByteArrayOutputStream()
    val os = new DataOutputStream(bs)
    os.write(block)
    os.writeBoolean(master)
    os.writeVarInt(lastProducerHeight.size)
    lastProducerHeight.foreach(p => {
      os.writeByteArray(p._1.toBin)
      os.writeVarInt(p._2)
    })
    bs.toByteArray
  }
}

object ForkItem {
  def fromBytes(bytes: Array[Byte]): ForkItem = {
    import com.apex.common.Serializable._
    val bs = new ByteArrayInputStream(bytes)
    val is = new DataInputStream(bs)
    val block = is.readObj(Block.deserialize)
    val master = is.readBoolean
    val lastProducerHeight = Map.empty[PublicKey, Int]
    for (_ <- 1 to is.readVarInt) {
      lastProducerHeight += PublicKey(is.readByteArray) -> is.readVarInt
    }
    ForkItem(block, lastProducerHeight, master)
  }
}

class ForkBase(dir: String, witnesses: Array[Witness], onConfirmed: Block => Unit) {
  private var _head: Option[ForkItem] = None

  val indexById = Map.empty[UInt256, ForkItem]
  val indexByPrev = MultiMap.empty[UInt256, UInt256]
  val indexByHeight = SortedMultiMap.empty[Int, Boolean, UInt256]()(implicitly[Ordering[Int]], implicitly[Ordering[Boolean]].reverse)
  val indexByConfirmedHeight = SortedMultiMap.empty[Int, Int, UInt256]()(implicitly[Ordering[Int]].reverse, implicitly[Ordering[Int]].reverse)

  val db = LevelDbStorage.open(dir)

  db.scan((_, v) => {
    val item = ForkItem.fromBytes(v)
    createIndex(item)
  })

  def head(): Option[ForkItem] = {
    _head
  }

  def get(id: UInt256): Option[ForkItem] = {
    indexById.get(id)
  }

  def add(block: Block): Boolean = {
    val lph = mutable.Map.empty[PublicKey, Int]
    if (_head.isEmpty) {
      for (witness <- witnesses) {
        lph.put(witness.pubkey, 0)
      }
      lph.put(PublicKey(block.header.producer), block.header.index)
      add(ForkItem(block, lph))
    } else {
      if (!indexById.contains(block.id) && indexById.contains(block.header.prevBlock)) {
        val h = _head.get
        for (p <- h.lastProducerHeight) {
          lph.put(p._1, p._2)
        }
        lph.put(PublicKey(block.header.producer), block.header.index)
        add(ForkItem(block, lph))
      } else {
        println(s"${block.height}")
        false
      }
    }
  }

  def add(item: ForkItem): Boolean = {
    insert(item)
    _head = indexById.get(indexByConfirmedHeight.head._3)
    removeConfirmed(_head.get.confirmedHeight)
    true
  }

  def switchTo(id: UInt256): Unit = {
    val (originFork, newFork) = getForks(head(), indexById.get(id))

    val items = Seq.empty[ForkItem]
    db.batchWrite(batch => {
      for (item <- originFork) {
        val newItem = item.copy(master = false)
        batch.put(newItem.block.id.toBytes, newItem.toBytes)
        items :+ newItem
      }
      for (item <- newFork) {
        val newItem = item.copy(master = true)
        batch.put(newItem.block.id.toBytes, newItem.toBytes)
        items :+ newItem
      }
    })
    for (item <- items) {
      indexById.put(item.block.id, item)
    }
  }

  private def removeConfirmed(height: Int) = {
    val items = Seq.empty[ForkItem]
    for (p <- indexByHeight if p._1 < height) {
      if (indexById.contains(p._3)) {
        items :+ indexById(p._3)
      }
    }
    items.foreach(item => {
      if (item.master) {
        onConfirmed(item.block)
        db.batchWrite(batch => {
          batch.delete(item.block.id.toBytes)
          deleteIndex(item)
        })
      } else {
        removeFork(item.block.id)
      }
    })
  }

  private def removeFork(id: UInt256) = {
    val items = indexByPrev.get(id)
      .map(indexById.get)
      .filterNot(_.isEmpty)
      .map(_.get)

    db.batchWrite(batch => {
      items.map(_.block.id.toBytes).foreach(batch.delete)
      items.foreach(deleteIndex)
    })
  }

  private def insert(item: ForkItem): Boolean = {
    if (db.set(item.block.id.toBytes, item.toBytes)) {
      createIndex(item)
      true
    } else {
      false
    }
  }

  private def getForks(x: Option[ForkItem], y: Option[ForkItem]): (Seq[ForkItem], Seq[ForkItem]) = {
    if (x.isEmpty || y.isEmpty) {
      throw new InvalidOperationException("")
    } else {
      var a = x.get
      var b = y.get
      if (a.block.id.equals(b.block.id)) {
        (Seq(a), Seq(b))
      } else {
        val xs = Seq.empty[ForkItem]
        val ys = Seq.empty[ForkItem]
        while (a.block.header.index < b.block.header.index) {
          xs :+ a
          a = getPrev(a)
        }
        while (b.block.header.index < a.block.header.index) {
          ys :+ b
          b = getPrev(b)
        }
        while (!a.block.id.equals(b.block.id)) {
          xs :+ a
          ys :+ b
          a = getPrev(a)
          b = getPrev(b)
        }
        (xs, ys)
      }
    }
  }

  private def getPrev(item: ForkItem): ForkItem = {
    val prev = get(item.block.header.prevBlock)
    if (prev.isEmpty) {
      throw new UnExpectedError
    }
    prev.get
  }

  private def createIndex(item: ForkItem): Unit = {
    val blk = item.block
    indexById.put(blk.id, item)
    indexByPrev.put(blk.header.prevBlock, blk.id)
    indexByHeight.put(blk.header.index, item.master, blk.id)
    indexByConfirmedHeight.put(item.confirmedHeight, blk.header.index, blk.id)
  }

  private def deleteIndex(item: ForkItem): Unit = {
    val blk = item.block
    indexById.remove(blk.id)
    indexByPrev.remove(blk.header.prevBlock)
    indexByHeight.remove(blk.header.index, item.master)
    indexByConfirmedHeight.remove(item.confirmedHeight, blk.header.index)
  }
}
