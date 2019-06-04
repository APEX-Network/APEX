package com.apex.test

import java.time.Instant

import com.apex.consensus.{ProducerUtil, RegisterData, WitnessVoteData, WitnessInfo}
import com.apex.core._
import com.apex.crypto.{BinaryData, Crypto, Ecdsa, FixedNumber, MerkleTree, UInt160, UInt256}
import com.apex.crypto.Ecdsa.{PrivateKey, PublicKey}
import com.apex.settings.{ConsensusSettings, RuntimeParas, _}
import com.apex.solidity.Abi
import com.apex.vm.DataWord
import org.junit.{AfterClass, Test}

import scala.collection.mutable.ArrayBuffer
import scala.reflect.io.Directory

//======
//1
//priv key raw:           108fd85be78e0dcc96b93c9c53c30281115ee453e9c472654fad985dcd7b91db
//priv key WIF format:    KwmuSp41VWBtGSWaQQ82ZRRSFzkJVTAyuDLQ9NzP9CPqLWirh4UQ
//pub key (compressed):   024f3076ac9b2b8ef8bf1c303c3ac3057472c9fa391df62c543789f34017168c62
//pub key hash160:        c98bae4cc033c7e7bce053ebaa926bd61c120454
//Address:                APLLBNZjMq4tFANSNHsEoXwGJ7NVfi4BxUa
//======
//2
//priv key raw:           ad28867b93d8be8558d61ee58e971117142aefd28ebb54bf4f20792bfb7fab25
//priv key WIF format:    L32JpLopG2hWjEMSCkAjS1nUnPixVrDTPqFAGYbddQrtUjRfkjEP
//pub key (compressed):   02add2d02786ba350148aee109e67495d6c4c2dccd9b5aaa57ad866d7b6105ac8f
//pub key hash160:        2ee607c3ed304353dd8a2d16636b46bd91d11152
//Address:                AP6EUsRV9DCvbywpDFLXS3JhjRryFPe9Qo8
//======
//3
//priv key raw:           43427dd5def74e97ab0409eaec3d64aff0557707a6edc1004d1c6e08ea705b45
//priv key WIF format:    KyUTLv2BeP9SJD6Sa8aHBVmuRkgw9eThjNGJDE4PySEgf2TvCQCn
//pub key (compressed):   03d7a23792639a9824d1013151711b521653f0c72563d69481bd914db75b03309d
//pub key hash160:        00d08ce14267fbce154d2983b8723dd5ad2addd5
//Address:                AP22p497htDs1SKXHjwyCgrYuFtTgcP5F5w
//======
//4
//priv key raw:           adc2a556a1a1726ecce71eb38e306914af4d82f547a545eed677ba555409932f
//priv key WIF format:    L33Uh9L35pSoEqBPP43U6rQcD2xMpJ7F4b3QMjUMAL6HZhxUqEGq
//pub key (compressed):   03f5bb4aa2a0773d658e9df51865ffbfd41f98891bd1994337afae4dc27c1d1927
//pub key hash160:        da55e4f6f216187259e7bc56cbb1587edc5156b4
//Address:                APMrxRgE3ZBqw9nC249175AvjUcmYMCq7XX
//======
//5
//priv key raw:           2cd68534541d6babbc8edb7a15036f8cee5f3506d667aecc022ffa1ea1b1b269
//priv key WIF format:    KxisR46MUfkekvgfuuydTD91avsjxhoqs5S6Ech2uiG21RDUEbna
//pub key (compressed):   03300f032f1d130956f0ff994cbd52cab3fc4167c0f6e96f1b5714da10ed51c1bd
//pub key hash160:        63d15a28a09748540eca07aeabf3b831b3056ebe
//Address:                APB4Hur58nvWWHuSE5qNQFWYPjZaKZetFAe
//======
//6
//priv key raw:           0c165d5428409eef6e025d6fe827b3b0f595f5a1cc4051c9ff43a7f1e20fed56
//priv key WIF format:    KwdCy3jFbU5MK4ZMbgKLTDkenhQgCU44hevVVXAv7ZUZRYJypqCB
//pub key (compressed):   034a79dcbd7b6da30739516f00e0be2e8c511a9ee446cc7362b8a441504ec0e7bf
//pub key hash160:        3a01649c803b08d4a3522c3ca49dedf687d576f9
//Address:                AP7FD5j2aPj83dmb3xSJZVVJ1JwNLry74xa
//======
//7
//priv key raw:           29e286f51e12df09f46b451d606650a0cce4b0d6d42a5d2e5450ab7c8b58a2c3
//priv key WIF format:    Kxd8UDvptjG4AcT7k2ULe8tVkmfK5naXKc3gzrvRtg6msGLA3xLY
//pub key (compressed):   03a549ab71a681d09bd090ed67bad589e55b582a2bbbdf3fb7d68ad3a71bfee211
//pub key hash160:        3252379053bf9f8827896cf0a5ff2cbd6fdca06f
//Address:                AP6YaVweecsk2sLRpSdUEwiQ2yE9WvitBtr
//======

@Test
class BlockchainVoteTest2 {

  Directory("BlockchainVoteTest2").deleteRecursively()

  val _produceInterval = 500

  val _minerAward: Double = 12.3

  val _witness1 = InitWitness("init1",
    PublicKey("022ac01a1ea9275241615ea6369c85b41e2016abc47485ec616c3c583f1b92a5c8").pubKeyHash)
  //Some(new PrivateKey(BinaryData("efc382ccc0358f468c2a80f3738211be98e5ae419fc0907cb2f51d3334001471"))))

  val _witness2 = InitWitness("init2",
    PublicKey("03c3333373adc0636b1d67d4bca3d8b34a53d663698119369981e67866250d3a74").pubKeyHash)
  //Some(new PrivateKey(BinaryData("cc7b7fa6e706944fa2d75652065f95ef2f364316e172601320655aac0e648165"))))

  val _witness3 = InitWitness("init3",
    PublicKey("020550de6ce7ed53ff018cccf1095893edba43f798252d6983e0fd2ca5af3ee0da").pubKeyHash)
  //Some(new PrivateKey(BinaryData("db71fe7c0ac4ca3e8cef95bf55cf535eaa8fe0c80d18e0cb19af8d7071b8a184"))))

  val _witness4 = InitWitness("init4",  // APPnx5YahVg1dTgeWkp1fE33ftvAaGbeQaR  L2C4Za8VSx2iBgszQarHx4YzqHvfumkHjbi6bNqvqst6mc8QcuZ7
    PublicKey("0246f896de22582786884d7d7ae27ef00cc8fed167bcdb8c305fbbc3dd9cca696c").pubKeyHash)
  //Some(new PrivateKey(BinaryData("9456beec947b368eda4be03f6c306703d9b2eda49f661285944b4e1f07ae18f3"))))

  val _acct1 = Ecdsa.PrivateKey.fromWIF("KzxPrzMcoE5hYU3e9XTRS51dvo68waSBYm5tkSfyWpURhS6wW5VZ").get  // !!!
  val _acct2 = Ecdsa.PrivateKey.fromWIF("KwmuSp41VWBtGSWaQQ82ZRRSFzkJVTAyuDLQ9NzP9CPqLWirh4UQ").get  // !!!
  val _acct3 = Ecdsa.PrivateKey.fromWIF("KyUTLv2BeP9SJD6Sa8aHBVmuRkgw9eThjNGJDE4PySEgf2TvCQCn").get
  val _acct4 = Ecdsa.PrivateKey.fromWIF("L33Uh9L35pSoEqBPP43U6rQcD2xMpJ7F4b3QMjUMAL6HZhxUqEGq").get
  val _acct5 = Ecdsa.PrivateKey.fromWIF("KxisR46MUfkekvgfuuydTD91avsjxhoqs5S6Ech2uiG21RDUEbna").get

//  val _miners = MinerSettings(Array(
//    new PrivateKey(BinaryData("efc382ccc0358f468c2a80f3738211be98e5ae419fc0907cb2f51d3334001471")),
//    new PrivateKey(BinaryData("cc7b7fa6e706944fa2d75652065f95ef2f364316e172601320655aac0e648165")),
//    new PrivateKey(BinaryData("db71fe7c0ac4ca3e8cef95bf55cf535eaa8fe0c80d18e0cb19af8d7071b8a184")),
//    new PrivateKey(BinaryData("9456beec947b368eda4be03f6c306703d9b2eda49f661285944b4e1f07ae18f3")),
//    _acct1))

  val _miners3 = MinerSettings(Array(
    new PrivateKey(BinaryData("efc382ccc0358f468c2a80f3738211be98e5ae419fc0907cb2f51d3334001471")),
    new PrivateKey(BinaryData("cc7b7fa6e706944fa2d75652065f95ef2f364316e172601320655aac0e648165")),
    new PrivateKey(BinaryData("db71fe7c0ac4ca3e8cef95bf55cf535eaa8fe0c80d18e0cb19af8d7071b8a184"))    ))

  val _consensusSettings  = ConsensusSettings(_produceInterval, 500, 1, 4, 2000, Array(_witness1, _witness2, _witness3, _witness4))
  //val _consensusSettings2 = ConsensusSettings(_produceInterval, 500, 3, 4, 63000, Array(_witness1, _witness2, _witness3, _witness4))

  val _runtimeParas = RuntimeParas(100)

  private val minerCoinFrom = UInt160.Zero

  private def makeTx(from: PrivateKey,
                     to: PrivateKey,
                     amount: FixedNumber,
                     nonce: Long,
                     gasLimit: Long = 21000,
                     gasPrice: FixedNumber = FixedNumber.MinValue,
                     txType: TransactionType.Value = TransactionType.Transfer): Transaction = {

    val tx = new Transaction(txType, from.publicKey.pubKeyHash, to.publicKey.pubKeyHash,
      amount, nonce, BinaryData.empty, gasPrice, gasLimit, BinaryData.empty)
    tx.sign(from)
    tx
  }

//  private def makeBlock(chain: Blockchain,
//                        preBlock: Block,
//                        txs: Seq[Transaction],
//                        award: Double = _minerAward): Block = {
//    val blockTime = preBlock.header.timeStamp + _consensusSettings.produceInterval
//    val miner = chain.getWitness(blockTime)
//
//    val minerTx = new Transaction(TransactionType.Miner, minerCoinFrom,
//      miner, FixedNumber.fromDecimal(award), preBlock.height + 1,
//      BinaryData(Crypto.randomBytes(8)), // add random bytes to distinct different blocks with same block index during debug in some cases
//      FixedNumber.Zero, 0, BinaryData.empty)
//
//    val allTxs = ArrayBuffer.empty[Transaction]
//
//    allTxs.append(minerTx)
//    txs.foreach(allTxs.append(_))
//
//    val header: BlockHeader = BlockHeader.build(preBlock.header.index + 1,
//      blockTime, MerkleTree.root(allTxs.map(_.id)),
//      preBlock.id(), _miners.findPrivKey(miner).get)
//
//    Block.build(header, allTxs)
//  }

  private def makeBlock(chain: Blockchain,
                        preBlock: Block,
                        txs: Seq[Transaction],
                        award: Double = _minerAward): Block = {

    var produced = false
    var nextBlockTime: Long = preBlock.header.timeStamp + _produceInterval

    while (!produced) {
      val witness = chain.getWitness(nextBlockTime)
      if (_miners3.findPrivKey(witness).isDefined) {
        chain.startProduceBlock(_miners3.findPrivKey(witness).get, nextBlockTime, Long.MaxValue)
        txs.foreach(tx => {chain.addTransaction(tx)})
        chain.produceBlockFinalize()
        produced = true
      }
      else
        nextBlockTime += _produceInterval
    }
    chain.head
  }

//  private def insertBlocks(chain: Blockchain,
//                           preBlock: Block,
//                           blockCount: Int): Block = {
//
//  }

//  private def makeBlockByTime(chain: Blockchain, preBlock: Block,
//                              //txs: Seq[Transaction],
//                              blockTime: Long): Block = {
//    //val blockTime = preBlock.header.timeStamp + _consensusSettings.produceInterval
//    val miner = chain.getWitness(blockTime)
//
//    val minerTx = new Transaction(TransactionType.Miner, minerCoinFrom,
//      miner, FixedNumber.fromDecimal(_minerAward), preBlock.height + 1,
//      BinaryData(Crypto.randomBytes(8)), // add random bytes to distinct different blocks with same block index during debug in some cases
//      FixedNumber.Zero, 0, BinaryData.empty)
//
//    val allTxs = ArrayBuffer.empty[Transaction]
//
//    allTxs.append(minerTx)
//    //txs.foreach(allTxs.append(_))
//
//    val header: BlockHeader = BlockHeader.build(preBlock.header.index + 1,
//      blockTime, MerkleTree.root(allTxs.map(_.id)),
//      preBlock.id(), _miners.findPrivKey(miner).get)
//
//    Block.build(header, allTxs)
//  }

//  private def startProduceBlock(chain: Blockchain, blockTime: Long, stopProcessTxTime: Long) = {
//
//    val witness = chain.getWitness(blockTime)
//    chain.startProduceBlock(_miners.findPrivKey(witness).get, blockTime, stopProcessTxTime)
//  }

  private def createChain(path: String,
                          consensusSettings: ConsensusSettings = _consensusSettings): Blockchain = {
    val baseDir = s"BlockchainVoteTest2/$path"
    val chainSetting = ChainSettings(
      BlockBaseSettings(s"$baseDir/block", false, 0, DBType.LevelDB),
      DataBaseSettings(s"$baseDir/data", false, 0, DBType.LevelDB),
      ForkBaseSettings(s"$baseDir/fork", false, 0, DBType.LevelDB),
      _minerAward,
      GenesisSettings(Instant.EPOCH,
        "7a93d447bffe6d89e690f529a3a0bdff8ff6169172458e04849ef1d4eafd7f86",
        Array(CoinAirdrop(_acct1.publicKey.address, 200),
          CoinAirdrop(_acct2.publicKey.address, 300))
      )
    )

    new Blockchain(chainSetting, consensusSettings, _runtimeParas, Notification())
  }

  //  private def sleepTo(time: Long) = {
  //    val nowTime = Instant.now.toEpochMilli
  //    if (time > nowTime)
  //      Thread.sleep(time - nowTime)
  //  }

  private def createRegisterTransaction(//operationType: OperationType.Value = OperationType.register,
                              nonce: Long = 0,
                              from: PrivateKey,
                              name: String = "register node1",
                              isGenesisWitness: Boolean = false): Transaction = {
    val txData = RegisterData(
                    from.publicKey.pubKeyHash,
                    WitnessInfo(from.publicKey.pubKeyHash, isGenesisWitness, name),
                    OperationType.register).toBytes
    val registerContractAddr = new UInt160(DataWord.of("0000000000000000000000000000000000000000000000000000000000000101").getLast20Bytes)
    val tx = new Transaction(TransactionType.Call, from.publicKey.pubKeyHash, registerContractAddr,
      FixedNumber.Zero, nonce, txData, FixedNumber(1), 9000000L, BinaryData.empty)
    tx.sign(from)
    tx
  }

  private def createVoteTransaction(voter: PrivateKey,
                                  candidate: PrivateKey,
                                  operationType: OperationType.Value = OperationType.register,
                                  counter: FixedNumber = FixedNumber.One,
                                  nonce: Long = 0): Transaction = {
    val txData = WitnessVoteData(candidate.publicKey.pubKeyHash, counter, operationType).toBytes
    val registerContractAddr = new UInt160(DataWord.of("0000000000000000000000000000000000000000000000000000000000000102").getLast20Bytes)
    val tx = new Transaction(TransactionType.Call, voter.publicKey.pubKeyHash, registerContractAddr, FixedNumber.Zero,
      nonce, txData, FixedNumber(1), 9000000L, BinaryData.empty)
    tx.sign(voter)
    tx
  }

  @Test
  def testBasicRegVote(): Unit = {
    val chain = createChain("testBasicRegVote")
    try {

      assert(chain.getHeight() == 0)

      //makeBlock(chain, chain.head, Seq.empty)

      val block1 = makeBlock(chain, chain.head, Seq.empty)
      assert(chain.head.id() == block1.id())

      assert(chain.getProducers("all").witnesses.size == 4)
      assert(!chain.getProducers("all").contains(_acct2.publicKey.pubKeyHash))

      val block2 = makeBlock(chain, block1, Seq(createRegisterTransaction(0, _acct2)))

      assert(chain.getHeight() == 2)

      println("block2 inserted")

      assert(chain.getProducers("all").witnesses.size == 5)

      assert(chain.getProducers("all").contains(_acct2.publicKey.pubKeyHash))

      val block3 = makeBlock(chain, block2, Seq.empty)

      println("block3 inserted")

      val block4 = makeBlock(chain, block3, Seq.empty)

      assert(chain.getHeight() == 4)

      println("block4 inserted")
      assert(chain.getProducers("pending").contains(_acct2.publicKey.pubKeyHash))

      val block5 = makeBlock(chain, block4, Seq.empty)
      assert(chain.getHeight() == 5)
      println("block5 inserted")

      assert(chain.getProducers("pending").contains(_acct2.publicKey.pubKeyHash))

      val block6 = makeBlock(chain, block5, Seq.empty)

      assert(block6.height() == 6)
      assert(chain.getHeight() == 6)
      println("block6 inserted")

      val block7 = makeBlock(chain, block6, Seq.empty)
      assert(block7.height() == 7)
      assert(chain.getHeight() == 7)
      println("block7 inserted")

      println("_acct2 is " + _acct2.publicKey.pubKeyHash.address)
      assert(chain.getProducers("active").contains(_acct2.publicKey.pubKeyHash))

      val block8 = makeBlock(chain, block7, Seq.empty)
      assert(block8.height() == 8)
      assert(chain.getHeight() == 8)
      println("block8 inserted")

    }
    finally {
      chain.close()
    }
  }


}

object BlockchainVoteTest2 {
  @AfterClass
  def cleanUp: Unit = {
    println("clean Directory")
    Directory("BlockchainVoteTest2").deleteRecursively()
  }
}
