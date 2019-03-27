/*
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: RocksDBStorageTest.scala
 *
 * @author: fang.wu@chinapex.com: 18-7-25 下午2:09@version: 1.0
 */

package com.apex.test

import org.junit.{AfterClass, Test}

import scala.reflect.io.Directory

@Test
class RocksDBStorageTest {
  private final val testClass = "RocksDBStorageTest"

  @Test
  def testSet() = {
    val storage = RocksDbManager.open(testClass, "testSet")
    assert(storage.set("testSet".getBytes, "testSetValue".getBytes))
  }

  @Test
  def testGet() = {
    val key = "testGet".getBytes
    val valueString = "testGetValue"
    val storage = RocksDbManager.open(testClass, "testGet")
    assert(storage.set(key, valueString.getBytes))
    val value = storage.get(key)
    assert(value.exists(v => new String(v).equals(valueString)))
  }

  @Test
  def testUpdate() = {
    val key = "testUpdate".getBytes
    val valueString = "testUpdateValue"
    val newValueString = "testUpdateValueNew"
    val storage = RocksDbManager.open(testClass, "testUpdate")
    assert(true == storage.set(key, valueString.getBytes))
    val value = storage.get(key)
    assert(value.isDefined)
    assert(new String(value.get).equals(valueString))
    assert(true == storage.set(key, newValueString.getBytes))
    val newValue = storage.get(key)
    assert(newValue.isDefined)
    assert(new String(newValue.get).equals(newValueString))
  }

  @Test
  def testGetKeyNotExists() = {
    val storage = RocksDbManager.open(testClass, "testGetKeyNotExists")
    val value = storage.get("testNotExistKey".getBytes)
    assert(value.isEmpty)
  }

  @Test
  def testDelete() = {
    val key = "testDelete".getBytes
    val value = "testDeleteValue".getBytes
    val storage = RocksDbManager.open(testClass, "testDelete")
    assert(true == storage.set(key, value))
    assert(storage.get(key).isDefined)
    storage.delete(key)
    assert(storage.get(key).isEmpty)
  }

  @Test
  def testScan() = {
    val storage = RocksDbManager.open(testClass, "testScan")
    var pairs = collection.mutable.Seq.empty[(String, String)]
    for (i <- 1 to 10) {
      val key = s"key$i"
      val value = s"value$i"
      pairs = pairs :+ (key, value)
      if (storage.get(key.getBytes).isEmpty) {
        assert(storage.set(key.getBytes, value.getBytes))
      }
    }
    var i = 0
    pairs = pairs.sortBy(_._1)
    storage.scan((k, v) => {
      assert((new String(k), new String(v)).equals(pairs(i)))
      i += 1
    })
    assert(i == 10)
  }

  @Test
  def testFind() = {
    val storage = RocksDbManager.open(testClass, "testFind")
    val seqArr = Array(
      collection.mutable.Seq.empty[(String, String)],
      collection.mutable.Seq.empty[(String, String)]
    )
    val prefixes = Seq("key_a_", "key_b_")
    for (i <- 1 to 10) {
      val key = s"${prefixes(i % 2)}$i"
      val keyBytes = key.getBytes
      val value = s"value$i"
      seqArr(i % 2) = seqArr(i % 2) :+ (key, value)
      if (storage.get(keyBytes).isEmpty) {
        assert(storage.set(keyBytes, value.getBytes))
      }
    }

    for (j <- 0 to 1) {
      var i = 0
      val seq = seqArr(j).sortBy(_._1)
      storage.find(prefixes(j).getBytes, (k, v) => {
        assert((new String(k), new String(v)).equals(seq(i)))
        i += 1
      })
      assert(i == 5)
    }
  }

  @Test
  def testScanPrefix() = {
    DbManager.clearUp("RocksDBStorageTest")
    val storage = RocksDbManager.open(testClass, "testScanPrefix")
    val seqArr = Array(
      collection.mutable.Seq.empty[(String, String)],
      collection.mutable.Seq.empty[(String, String)]
    )
    val prefixes = Seq("key_a_", "key_b_")
    for (i <- 1 to 10) {
      val key = s"${prefixes(i % 2)}$i"
      val keyBytes = key.getBytes
      val value = s"value$i"
      seqArr(i % 2) = seqArr(i % 2) :+ (key, value)
      if (storage.get(keyBytes).isEmpty) {
        assert(storage.set(keyBytes, value.getBytes))
      }
    }

    val records = storage.scan(prefixes(0).getBytes)
    assert(records.size == 5)
    val a = records(0)
    val b = "key_a_10".getBytes
    assert(records(0).sameElements("value10".getBytes))
  }

  @Test
  def testLastEmpty(): Unit = {
    val storage = RocksDbManager.open(testClass, "testLastEmpty")
    assert(storage.last().isEmpty)
  }

  @Test
  def testLast(): Unit = {
    val storage = RocksDbManager.open(testClass, "testLast")
    for (i <- 0 to 10) {
      storage.set(BigInt(i).toByteArray, s"test$i".getBytes)
    }

    val last = storage.last.get
    assert(BigInt(last.getKey).toInt == 10)
    assert(new String(last.getValue).equals("test10"))
  }

//  @Test
  def testSession(): Unit = {

    def assertUncommittedSessions(levels: Seq[Long], min: Int, max: Int) = {
      assert(levels.length == max - min + 1)
      var start = min
      for (elem <- levels) {
        assert(elem == start)
        start += 1
      }
      assert(start == max + 1)
    }

    val testMethod = "testSession"

    {
      val db = DbManager.open(testClass, testMethod)
      try {
        db.newSession()
        assert(db.revision() == 2)
        assertUncommittedSessions(db.uncommitted(), 1, 1)
      } finally {
        DbManager.close(testClass, testMethod)
      }
    }
    {
      val db = DbManager.open(testClass, testMethod)
      try {
        db.newSession()
        assert(db.revision() == 3)
        assertUncommittedSessions(db.uncommitted(), 1, 2)
      } finally {
        DbManager.close(testClass, testMethod)
      }
    }
    {
      val db = DbManager.open(testClass, testMethod)
      try {
        assert(db.revision() == 3)
        db.rollBack()
        assert(db.revision() == 2)
        assertUncommittedSessions(db.uncommitted(), 1, 1)
      } finally {
        DbManager.close(testClass, testMethod)
      }
    }
    {
      val db = DbManager.open(testClass, testMethod)
      try {
        assert(db.revision() == 2)
        db.commit()
        assert(db.uncommitted().isEmpty)
      } finally {
        DbManager.close(testClass, testMethod)
      }
    }
    {
      val db = DbManager.open(testClass, testMethod)
      try {
        assert(db.revision() == 2)
        assert(db.uncommitted().isEmpty)
        db.newSession()
        db.newSession()
        db.newSession()
        db.newSession()
        db.newSession()
        db.newSession()
        assert(db.revision() == 8)
      } finally {
        DbManager.close(testClass, testMethod)
      }
    }
    {
      val db = DbManager.open(testClass, testMethod)
      try {
        assert(db.revision() == 8)
        assertUncommittedSessions(db.uncommitted(), 2, 7)
        db.commit(5)
        assertUncommittedSessions(db.uncommitted(), 6, 7)
      } finally {
        DbManager.close(testClass, testMethod)
      }
    }
    {
      val db = DbManager.open(testClass, testMethod)
      try {
        assert(db.revision() == 8)
        assertUncommittedSessions(db.uncommitted(), 6, 7)
        db.rollBack()
        db.rollBack()
        assert(db.uncommitted().isEmpty)
        assert(db.revision() == 6)
      } finally {
        DbManager.close(testClass, testMethod)
      }
    }
    {
      val db = DbManager.open(testClass, testMethod)
      try {
        assert(db.uncommitted().isEmpty)
        assert(db.revision() == 6)
        println(s"final revision ${db.revision()}")
      } finally {
        DbManager.close(testClass, testMethod)
      }
    }
  }
}


object RocksDBStorageTest {

  @AfterClass
  def cleanUp: Unit = {
    Directory("RocksDBStorageTest").deleteRecursively()
    RocksDbManager.clearUp("RocksDBStorageTest")
  }
}


