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
 *
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: Program.scala
 *
 * @author: ruixiao.xiao@chinapex.com: 18-11-28 下午1:43@version: 1.0
 *
 */

package com.apex.vm.program

import java.util

import com.apex.common.ApexLogging
import com.apex.core.DataBase
import com.apex.crypto.UInt160
import com.apex.settings.ContractSettings
import com.apex.vm.exceptions._
import com.apex.vm.program.invoke.ProgramInvoke
import com.apex.vm.program.listener.{CompositeProgramListener, ProgramListenerAware, ProgramStorageChangeListener}
import com.apex.vm.program.trace.{ProgramTrace, ProgramTraceListener}
import com.apex.vm.{DataWord, MessageCall, PrecompiledContract}
import org.apex.vm.{OpCache, OpCode}

class Program(settings: ContractSettings, ops: Array[Byte], invoke: ProgramInvoke) extends ApexLogging {
  private final val MAX_STACKSIZE = 1024
  /**
    * This attribute defines the number of recursive calls allowed in the EVM
    * Note: For the JVM to reach this level without a StackOverflow exception,
    * ethereumj may need to be started with a JVM argument to increase
    * the stack size. For example: -Xss10m
    */
  private final val MAX_DEPTH = 1024

  private var listener: ProgramOutListener = _
  private var traceListener: ProgramTraceListener = new ProgramTraceListener(settings.vmTrace)
  private val storageDiffListener = new ProgramStorageChangeListener
  private val programListener = new CompositeProgramListener

  private val result = new ProgramResult
  private var trace = new ProgramTrace

  private val memory = setupProgramListener(new Memory)
  private val stack: Stack = setupProgramListener(new Stack)

  private var programPrecompile: ProgramPrecompile = _

  private var returnDataBuffer: Array[Byte] = _
  private var previouslyExecutedOp: Byte = _
  private var stopped: Boolean = _
  private var lastOp: Byte = _
  private var pc: Int = _

  import com.apex.vm._

  def getCurrentOp(): Byte = if (ops.isEmpty) 0 else ops(pc)

  def setLastOp(op: Byte): Unit = lastOp = op

  /**
    * Should be set only after the OP is fully executed.
    */
  def setPreviouslyExecutedOp(op: Byte): Unit = previouslyExecutedOp = op

  /**
    * Returns the last fully executed OP.
    */
  def getPreviouslyExecutedOp: Byte = this.previouslyExecutedOp

  def stackPush(data: Array[Byte]): Unit = {
    stackPush(DataWord.of(data))
  }

  def stackPushZero(): Unit = {
    stackPush(DataWord.ZERO)
  }

  def stackPushOne(): Unit = {
    val stackWord = DataWord.ONE
    stackPush(stackWord)
  }

  def stackPush(stackWord: DataWord): Unit = {
    verifyStackOverflow(0, 1) //Sanity Check
    stack.push(stackWord)
  }

  def getMemSize: Int = memory.size

  def memorySave(addrB: DataWord, value: DataWord): Unit = {
    memory.write(addrB.intValue, value.getData, value.getData.length, false)
  }

  def memorySaveLimited(addr: Int, data: Array[Byte], dataSize: Int): Unit = {
    memory.write(addr, data, dataSize, true)
  }

  def memorySave(addr: Int, value: Array[Byte]): Unit = {
    memory.write(addr, value, value.length, false)
  }

  def memoryExpand(outDataOffs: DataWord, outDataSize: DataWord): Unit = {
    if (!outDataSize.isZero) memory.extend(outDataOffs.intValue, outDataSize.intValue)
  }

  /**
    * Allocates a piece of memory and stores value at given offset address
    *
    * @param addr      is the offset address
    * @param allocSize size of memory needed to write
    * @param value     the data to write to memory
    */
  def memorySave(addr: Int, allocSize: Int, value: Array[Byte]): Unit = {
    memory.extendAndWrite(addr, allocSize, value)
  }

  def memoryLoad(addr: DataWord): DataWord = memory.readWord(addr.intValue)

  def memoryLoad(address: Int): DataWord = memory.readWord(address)

  def memoryChunk(offset: Int, size: Int): Array[Byte] = memory.read(offset, size)

  def getStack: Stack = stack

  def getGasLong: Long = invoke.getGasLong - result.getGasUsed

  def getGas: DataWord = DataWord.of(invoke.getGasLong - result.getGasUsed)

  def getPC: Int = pc

  def setPC(pc: DataWord): Unit = setPC(pc.intValue)

  def setPC(pc: Int): Unit = {
    this.pc = pc
    if (this.pc >= ops.length) {
      stop()
    }
  }

  def isStopped: Boolean = stopped

  def stop(): Unit = stopped = true

  def setHReturn(buff: Array[Byte]): Unit = result.setHReturn(buff)

  def step(): Unit = setPC(pc + 1)

  def sweep(n: Int): Array[Byte] = {
    if (pc + n > ops.length) stop()
    val data = util.Arrays.copyOfRange(ops, pc, pc + n)
    pc += n
    if (pc >= ops.length) stop()
    data
  }

  def getPrevHash: DataWord = invoke.getPrevHash

  def getCoinbase: DataWord = invoke.getCoinbase

  def getTimestamp: DataWord = invoke.getTimestamp

  def getNumber: DataWord = invoke.getNumber

  def getDifficulty: DataWord = invoke.getDifficulty

  def getGasLimit: DataWord = invoke.getGaslimit

  def isStaticCall: Boolean = invoke.isStaticCall

  def getResult: ProgramResult = result

  def setRuntimeFailure(e: RuntimeException): Unit = {
    getResult.setException(e)
  }

  def getCallDeep: Int = invoke.getCallDeep

  def stackPop: DataWord = stack.pop

  def verifyStackSize(stackSize: Int) = {
    if (stack.size < stackSize) {
      throw StackTooSmallException(stackSize, stack.size)
    }
  }

  def verifyStackOverflow(argsReqs: Int, returnReqs: Int): Unit = {
    if ((stack.size - argsReqs + returnReqs) > MAX_STACKSIZE) {
      throw StackTooLargeException(MAX_STACKSIZE)
    }
  }

  def getCode: Array[Byte] = ops

  def getCodeAt(address: DataWord): Array[Byte] = {
    invoke.getDataBase.getCode(address.toUInt160)
  }

  def getCodeHashAt(address: DataWord): Array[Byte] = {
    //    val state = invoke.getRepository.getAccountState(address.getLast20Bytes)
    //    // return 0 as a code hash of empty account (an account that would be removed by state clearing)
    //    if (state != null && state.isEmpty) EMPTY_BYTE_ARRAY
    //    else {
    //      val code = invoke.getRepository.getCodeHash(address.getLast20Bytes)
    //      nullToEmpty(code)
    //    }
    throw new NotImplementedError
  }

  def getOwnerAddress: DataWord = invoke.getOwnerAddress

  def getBlockHash(index: Int): DataWord = {
    //    if (index < getNumber.longValue && index >= Math.max(256, this.getNumber.intValue) - 256) {
    //      DataWord.of(invoke.getBlockStore.getBlockHashByNumber(index, getPrevHash.getData))
    //    } else {
    //      DataWord.ZERO
    //    }
    throw new NotImplementedError
  }

  def getBalance(address: DataWord): DataWord = {
    //    val balance = getStorage.getBalance(address.getLast20Bytes)
    //    DataWord.of(balance.toByteArray)
    throw new NotImplementedError
  }

  def getOriginAddress: DataWord = invoke.getOriginAddress

  def getCallerAddress: DataWord = invoke.getCallerAddress

  def getGasPrice: DataWord = invoke.getMinGasPrice

  def getCallValue: DataWord = invoke.getCallValue

  def getDataSize: DataWord = invoke.getDataSize

  def getDataValue(index: DataWord): DataWord = invoke.getDataValue(index)

  def getDataCopy(offset: DataWord, length: DataWord): Array[Byte] = invoke.getDataCopy(offset, length)

  def getReturnDataBufferSize: DataWord = DataWord.of(getReturnDataBufferSizeI)

  def getStorage: DataBase = invoke.getDataBase

  /**
    * Create contract for OpCode#CREATE
    *
    * @param value    Endowment
    * @param memStart Code memory offset
    * @param memSize  Code memory size
    */
  def createContract(value: DataWord, memStart: DataWord, memSize: DataWord): Unit = {
    returnDataBuffer = null // reset return buffer right before the call

    val senderAddress = this.getOwnerAddress.getLast20Bytes
    val endowment = value.value
    if (verifyCall(senderAddress, endowment)) {
      //    val nonce = getStorage.getNonce(senderAddress).toByteArray
      //    val contractAddress = HashUtil.calcNewAddr(senderAddress, nonce)
      //    val programCode = memoryChunk(memStart.intValue, memSize.intValue)
      //    createContractImpl(value, programCode, contractAddress)
    }
    throw new NotImplementedError
  }

  /**
    * Create contract for OpCode#CREATE2
    *
    * @param value    Endowment
    * @param memStart Code memory offset
    * @param memSize  Code memory size
    * @param salt     Salt, used in contract address calculation
    */
  def createContract2(value: DataWord, memStart: DataWord, memSize: DataWord, salt: DataWord): Unit = {
    returnDataBuffer = null // reset return buffer right before the call

    val senderAddress = this.getOwnerAddress.getLast20Bytes
    val endowment = value.value
    if (!verifyCall(senderAddress, endowment)) return
    val programCode = memoryChunk(memStart.intValue, memSize.intValue)
    //    val contractAddress = HashUtil.calcSaltAddr(senderAddress, programCode, salt.getData)
    //    createContractImpl(value, programCode, contractAddress)
    throw new NotImplementedError
  }

  /**
    * Verifies CREATE attempt
    */
  private def verifyCall(senderAddress: Array[Byte], endowment: BigInt): Boolean = {
    //    if (getCallDeep == MAX_DEPTH) {
    //      stackPushZero()
    //      false
    //    } else if (getStorage.getBalance(senderAddress) < endowment) {
    //      stackPushZero()
    //      false
    //    } else {
    //      true
    //    }
    throw new NotImplementedError
  }

  /**
    * All stages required to create contract on provided address after initial check
    *
    * @param value       Endowment
    * @param programCode Contract code
    * @param newAddress  Contract address
    */
  private def createContractImpl(value: DataWord, programCode: Array[Byte], newAddress: Array[Byte]): Unit = { // [1] LOG, SPEND GAS
    import com.apex.vm._
    val senderAddress = getOwnerAddress.getLast20Bytes
    if (log.isInfoEnabled) log.info(s"creating a new contract inside contract run: [${senderAddress.toHex}]")

    throw new NotImplementedError
  }

  private def getReturnDataBufferSizeI = {
    if (returnDataBuffer == null) 0 else returnDataBuffer.length
  }

  def getReturnDataBufferData(off: DataWord, size: DataWord): Array[Byte] = {
    if (off.intValueSafe.toLong + size.intValueSafe > getReturnDataBufferSizeI) {
      null
    } else {
      if (returnDataBuffer == null) {
        new Array[Byte](0)
      } else {
        util.Arrays.copyOfRange(returnDataBuffer, off.intValueSafe, off.intValueSafe + size.intValueSafe)
      }
    }
  }

  def storageLoad(key: DataWord): DataWord = {
    val address = getOwnerAddress.toUInt160
    val value = invoke.getDataBase.getContractState(address, key.data)
    DataWord.of(value)
  }

  def storageSave(word1: DataWord, word2: DataWord): Unit = {
    storageSave(word1.getData, word2.getData)
  }

  def storageSave(key: Array[Byte], value: Array[Byte]): Unit = {
    val address = getOwnerAddress.toUInt160
    invoke.getDataBase.saveContractState(address, key, value)
  }

  /**
    * @return current Storage data for key
    */
  def getCurrentValue(key: DataWord): DataWord = {
    val address = getOwnerAddress.toUInt160
    val value = invoke.getDataBase.getContractState(address, key.data)
    DataWord.of(value)
  }

  /*
     * Original storage value at the beginning of current frame execution
     * For more info check EIP-1283 https://eips.ethereum.org/EIPS/eip-1283
     * @return Storage data at the beginning of Program execution
     */
  def getOriginalValue(key: DataWord): DataWord = {
    val address = getOwnerAddress.toUInt160
    val value = invoke.getOrigDataBase.getContractState(address, key.data)
    DataWord.of(value)
  }

  def fullTrace(): Unit = {
    //    if (log.isTraceEnabled || listener != null) {
    //      val stackData = new StringBuilder
    //      for (i <- 0 to stack.size - 1) {
    //        stackData.append(" ").append(stack.get(i))
    //        if (i < stack.size - 1) stackData.append("\n")
    //      }
    //      if (stackData.length > 0) stackData.insert(0, "\n")
    //    }
  }

  def saveOpTrace(): Unit = {
    if (pc < ops.length) {
      trace.addOp(ops(pc), pc, getCallDeep, getGas.value, traceListener.resetActions)
    }
  }

  def getTrace: ProgramTrace = trace

  def spendGas(gasValue: Long, cause: String): Unit = {
    if (log.isDebugEnabled) {
      log.debug(s"[${invoke.hashCode}] Spent for cause: [$cause], gas: [$gasValue]")
    }
    if (getGasLong < gasValue) {
      throw Program.notEnoughSpendingGas(cause, gasValue, invoke, result)
    }
    result.spendGas(gasValue)
  }

  def spendAllGas(): Unit = {
    spendGas(getGas.longValue, "Spending all remaining")
  }

  def refundGas(gasValue: Long, cause: String): Unit = {
    log.info(s"[${invoke.hashCode}] Refund for cause: [${cause}], gas: [${gasValue}]")
    result.refundGas(gasValue)
  }

  def futureRefundGas(gasValue: Long): Unit = {
    log.info(s"Future refund added: [$gasValue]")
    result.addFutureRefund(gasValue)
  }

  def resetFutureRefund(): Unit = {
    result.resetFutureRefund()
  }

  def addListener(outListener: ProgramOutListener): Unit = {
    listener = outListener
  }

  def verifyJumpDest(nextPC: DataWord): Int = {
    if (nextPC.bytesOccupied > 4) {
      throw Program.badJumpDestination(-1)
    }

    val ret = nextPC.intValue
    if (!getProgramPrecompile.hasJumpDest(ret)) {
      throw Program.badJumpDestination(ret)
    }
    ret
  }

  def getProgramPrecompile(): ProgramPrecompile = {
    if (programPrecompile == null) {
      programPrecompile = ProgramPrecompile.compile(ops)
    }
    programPrecompile
  }

  def callToPrecompiledAddress(msg: MessageCall, contract: PrecompiledContract): Unit = {
    returnDataBuffer = null // reset return buffer right before the call

    if (getCallDeep == MAX_DEPTH) {
      stackPushZero()
      refundGas(msg.gas.longValue, " call deep limit reach")
    } else {
      val track = getStorage.startTracking

      val senderAddress = getOwnerAddress.toUInt160
      val codeAddress = msg.codeAddress.toUInt160
      val stateLess = OpCache.fromCode(msg.opCode.value).callIsStateless
      val contextAddress = if (stateLess) senderAddress else codeAddress
      if (track.getBalance(senderAddress).forall(_.value < msg.endowment.value)) {
        stackPushZero()
        refundGas(msg.gas.longValue, "refund gas from message call")
      } else {
        val data = memoryChunk(msg.inDataOffs.intValue, msg.inDataSize.intValue)
        // Charge for endowment - is not reversible by rollback
        track.transfer(senderAddress, contextAddress, msg.endowment.value)

        if (byTestingSuite) { // This keeps track of the calls created for a test
          getResult.addCallCreate(data, msg.codeAddress.getLast20Bytes, msg.gas.getNoLeadZeroesData, msg.endowment.getNoLeadZeroesData)
          stackPushOne()
        } else {
          val requiredGas = contract.getGasForData(data)
          if (requiredGas > msg.gas.longValue) {
            refundGas(0, "call pre-compiled") //matches cpp logic
            stackPushZero()
            //track.rollback()
          } else {
            if (log.isDebugEnabled) {
              log.debug(s"Call ${contract.getClass.getSimpleName}(data = ${data.toHex})")
            }

            val (succeed, result) = contract.execute(data)
            if (succeed) { // success
              refundGas(msg.gas.longValue - requiredGas, "call pre-compiled")
              stackPushOne()
              returnDataBuffer = result
              track.commit()
            }
            else { // spend all gas on failure, push zero and revert state changes
              refundGas(0, "call pre-compiled")
              stackPushZero()
              //track.rollback()
            }
          }
        }
      }
    }
  }

  def callToAddress(msg: MessageCall): Unit = {
    throw new NotImplementedError
  }

  def suicide(obtainerAddress: DataWord): Unit = {
    throw new NotImplementedError
  }

  def byTestingSuite: Boolean = invoke.byTestingSuite

  private def setupProgramListener[T <: ProgramListenerAware](programListenerAware: T) = {
    if (programListener.isEmpty) {
      programListener.addListener(traceListener)
      programListener.addListener(storageDiffListener)
    }
    programListenerAware.setProgramListener(programListener)
    programListenerAware
  }
}

object Program {

  def notEnoughOpGas(op: OpCode.Value, opGas: DataWord, programGas: DataWord): OutOfGasException = {
    notEnoughOpGas(op, opGas.longValue, programGas.longValue)
  }

  def notEnoughOpGas(op: OpCode.Value, opGas: BigInt, programGas: BigInt): OutOfGasException = {
    notEnoughOpGas(op, opGas.toLong, programGas.toLong)
  }

  def notEnoughOpGas(op: OpCode.Value, opGas: Long, programGas: Long) = {
    OutOfGasException(s"Not enough gas for '$op' operation executing: opGas[$opGas], programGas[$programGas]")
  }


  def notEnoughSpendingGas(cause: String, gasValue: Long, invoke: ProgramInvoke, result: ProgramResult) = {
    OutOfGasException(s"Not enough gas for '$cause' cause spending: invokeGas[${invoke.getGas.longValue}], gas[$gasValue], usedGas[${result.getGasUsed}]")
  }

  def gasOverflow(actualGas: BigInt, gasLimit: BigInt) = {
    OutOfGasException(s"Gas value overflow: actualGas[$actualGas], gasLimit[$gasLimit]")
  }

  def returnDataCopyIllegalBoundsException(off: DataWord, size: DataWord, returnDataSize: Long) = {
    ReturnDataCopyIllegalBoundsException(s"Illegal RETURNDATACOPY arguments: offset ($off) + size (${size.intValue}) > RETURNDATASIZE ($returnDataSize)")
  }

  def staticCallModificationException() = {
    StaticCallModificationException("Attempt to call a state modifying opcode inside STATICCALL")
  }

  def badJumpDestination(pc: Int) = {
    BadJumpDestinationException(pc)
  }

  def invalidOpCode(code: OpCode.Value) = {
    IllegalOperationException(code.value)
  }
}

trait ProgramOutListener {
  def output(out: String): Unit
}