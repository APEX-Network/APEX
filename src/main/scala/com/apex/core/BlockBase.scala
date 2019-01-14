/*
 *
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: BlockBase.scala
 *
 * @author: ruixiao.xiao@chinapex.com: 18-9-27 上午11:32@version: 1.0
 *
 */

package com.apex.core

import com.apex.crypto.UInt256
import com.apex.settings.BlockBaseSettings
import com.apex.storage.Storage

class BlockBase(settings: BlockBaseSettings) {
  private val db = Storage.open(settings.dbType, settings.dir)

  private val blockStore = new BlockStore(db, settings.cacheSize)
  private val heightStore = new HeightStore(db, settings.cacheSize)
  private val headBlockStore = new HeadBlockStore(db)

  def head(): Option[BlockHeader] = {
    headBlockStore.get()
  }

  def add(block: Block): Unit = {
    require(head.forall(_.id.equals(block.prev)))

    db.batchWrite(batch => {
      blockStore.set(block.id, block, batch)
      heightStore.set(block.height, block.id, batch)
      headBlockStore.set(block.header, batch)
    })
  }

  def getBlock(id: UInt256): Option[Block] = {
    blockStore.get(id)
  }

  def getBlock(height: Int): Option[Block] = {
    heightStore.get(height).flatMap(getBlock)
  }

  def containBlock(id: UInt256): Boolean = {
    blockStore.contains(id)
  }

  def close(): Unit = {
    db.close()
  }
}