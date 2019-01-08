/*
 *
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: VMTest.scala
 *
 * @author: ruixiao.xiao@chinapex.com: 18-11-30 下午7:40@version: 1.0
 *
 */

package com.apex.test

import java.util

import com.apex.core.DataBase
import com.apex.crypto.Ecdsa.PublicKey
import com.apex.crypto.{BinaryData, Crypto}
import com.apex.settings.{ContractSettings, DataBaseSettings}
import com.apex.solidity.Abi
import com.apex.vm.hook.VMHook
import com.apex.vm.program.Program
import com.apex.vm.program.invoke.{ProgramInvoke, ProgramInvokeImpl}
import com.apex.vm.{DataWord, VM}
import org.apex.vm.OpCode
import org.junit.{AfterClass, Test}

import scala.reflect.io.Directory

@Test
class VMTest {

  @Test
  def test: Unit = {
    val _ =
      "contract D {\n   " +
        " mapping(address => uint) test;\n\n   " +
        " function set(uint i) public {\n        " +
        "   test[msg.sender] = i;\n   " +
        " }\n\n   " +
        " function get() public returns (uint) {\n        " +
        "   return test[msg.sender];\n   " +
        " }\n" +
        "}"
    val abi = Abi.fromJson("[{\"constant\":false,\"inputs\":[{\"name\":\"i\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}]")

    {
      val code = BinaryData("608060405260043610610046576000357c01000000000000000000000000000000000000000000000000000000009004806360fe47b11461004b5780636d4ce63c14610086575b600080fd5b34801561005757600080fd5b506100846004803603602081101561006e57600080fd5b81019080803590602001909291905050506100b1565b005b34801561009257600080fd5b5061009b6100f7565b6040518082815260200191505060405180910390f35b806000803373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000208190555050565b60008060003373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000205490509056fea165627a7a72305820b698ee72285f30209458db25efe0fe5632e08930f7659a0ac5208217d38d92500029").data.toArray
      val vmSettings = ContractSettings(0, false)
      val invoker = VMTest.createInvoker(abi.encode("set(0x777)"))
      val program = new Program(vmSettings, code, invoker)
      val vm = new VM(vmSettings, VMHook.EMPTY)
      vm.play(program)
      println(util.Arrays.toString(program.getResult.getHReturn))
    }

    println("-----------------------------")

    {
      val code = BinaryData("608060405260043610610046576000357c01000000000000000000000000000000000000000000000000000000009004806360fe47b11461004b5780636d4ce63c14610086575b600080fd5b34801561005757600080fd5b506100846004803603602081101561006e57600080fd5b81019080803590602001909291905050506100b1565b005b34801561009257600080fd5b5061009b6100f7565b6040518082815260200191505060405180910390f35b806000803373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000208190555050565b60008060003373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000205490509056fea165627a7a72305820b698ee72285f30209458db25efe0fe5632e08930f7659a0ac5208217d38d92500029").data.toArray
      val vmSettings = ContractSettings(0, false)
      val invoker = VMTest.createInvoker(abi.encode("get()"))
      val program = new Program(vmSettings, code, invoker)
      val vm = new VM(vmSettings, VMHook.EMPTY)
      vm.play(program)
      println(util.Arrays.toString(program.getResult.getHReturn))
      assert(DataWord.of(program.getResult.getHReturn).value == 0x777)
    }

    //        val code = Array[Byte](96, 96, 96, 64, 82, 52, 21, 97, 0, 15, 87, 96, 0, -128, -3, 91, 97, 1, -16, -128, 97, 0, 30, 96, 0, 57, 96, 0, -13, 0, 96, 96, 96, 64, 82, 96, 4, 54, 16, 97, 0, 98, 87, 96, 0, 53, 124, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -112, 4, 99, -1, -1, -1, -1, 22, -128, 99, 44, 4, 71, 121, 20, 97, 0, 103, 87, -128, 99, 69, 112, -108, -52, 20, 97, 0, -68, 87, -128, 99, 78, 112, -79, -36, 20, 97, 0, -47, 87, -128, 99, 96, -2, 71, -79, 20, 97, 0, -6, 87, 91, 96, 0, -128, -3, 91, 52, 21, 97, 0, 114, 87, 96, 0, -128, -3, 91, 97, 0, 122, 97, 1, 29, 86, 91, 96, 64, 81, -128, -126, 115, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 22, 115, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 22, -127, 82, 96, 32, 1, -111, 80, 80, 96, 64, 81, -128, -111, 3, -112, -13, 91, 52, 21, 97, 0, -57, 87, 96, 0, -128, -3, 91, 97, 0, -49, 97, 1, 37, 86, 91, 0, 91, 52, 21, 97, 0, -36, 87, 96, 0, -128, -3, 91, 97, 0, -28, 97, 1, -113, 86, 91, 96, 64, 81, -128, -126, -127, 82, 96, 32, 1, -111, 80, 80, 96, 64, 81, -128, -111, 3, -112, -13, 91, 52, 21, 97, 1, 5, 87, 96, 0, -128, -3, 91, 97, 1, 27, 96, 4, -128, -128, 53, -112, 96, 32, 1, -112, -111, -112, 80, 80, 97, 1, -107, 86, 91, 0, 91, 96, 0, 51, -112, 80, -112, 86, 91, 127, 5, -57, 102, -47, -59, -22, 111, 64, -81, -61, -116, -40, -30, 115, 8, -62, 54, -60, -110, -5, -49, -93, 43, 69, -115, 39, 85, -49, 118, -20, 30, 33, 96, 64, 81, -128, -128, 96, 32, 1, -126, -127, 3, -126, 82, 96, 4, -127, 82, 96, 32, 1, -128, 127, 102, 105, 114, 101, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -127, 82, 80, 96, 32, 1, -111, 80, 80, 96, 64, 81, -128, -111, 3, -112, -95, 86, 91, 96, 0, 84, -127, 86, 91, -128, 96, 0, -127, -112, 85, 80, 97, 34, 34, 96, 1, 2, 97, 17, 17, 96, 64, 81, -128, -126, 96, 1, 2, 96, 0, 25, 22, -127, 82, 96, 32, 1, -111, 80, 80, 96, 64, 81, -128, -111, 3, -112, -95, 80, 86, 0, -95, 101, 98, 122, 122, 114, 48, 88, 32, -121, -35, 34, 93, 122, -19, 95, -92, -1, -29, 108, -128, -54, -55, -79, -52, 23, 125, -42, 54, 38, -119, -44, -87, 61, -57, -70, 101, -45, 77, 11, -73, 0, 41)
    {
      val code = Array[Byte](96, 96, 96, 64, 82, 96, 4, 54, 16, 97, 0, 98, 87, 96, 0, 53, 124, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -112, 4, 99, -1, -1, -1, -1, 22, -128, 99, 44, 4, 71, 121, 20, 97, 0, 103, 87, -128, 99, 69, 112, -108, -52, 20, 97, 0, -68, 87, -128, 99, 78, 112, -79, -36, 20, 97, 0, -47, 87, -128, 99, 96, -2, 71, -79, 20, 97, 0, -6, 87, 91, 96, 0, -128, -3, 91, 52, 21, 97, 0, 114, 87, 96, 0, -128, -3, 91, 97, 0, 122, 97, 1, 29, 86, 91, 96, 64, 81, -128, -126, 115, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 22, 115, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 22, -127, 82, 96, 32, 1, -111, 80, 80, 96, 64, 81, -128, -111, 3, -112, -13, 91, 52, 21, 97, 0, -57, 87, 96, 0, -128, -3, 91, 97, 0, -49, 97, 1, 37, 86, 91, 0, 91, 52, 21, 97, 0, -36, 87, 96, 0, -128, -3, 91, 97, 0, -28, 97, 1, -113, 86, 91, 96, 64, 81, -128, -126, -127, 82, 96, 32, 1, -111, 80, 80, 96, 64, 81, -128, -111, 3, -112, -13, 91, 52, 21, 97, 1, 5, 87, 96, 0, -128, -3, 91, 97, 1, 27, 96, 4, -128, -128, 53, -112, 96, 32, 1, -112, -111, -112, 80, 80, 97, 1, -107, 86, 91, 0, 91, 96, 0, 51, -112, 80, -112, 86, 91, 127, 5, -57, 102, -47, -59, -22, 111, 64, -81, -61, -116, -40, -30, 115, 8, -62, 54, -60, -110, -5, -49, -93, 43, 69, -115, 39, 85, -49, 118, -20, 30, 33, 96, 64, 81, -128, -128, 96, 32, 1, -126, -127, 3, -126, 82, 96, 4, -127, 82, 96, 32, 1, -128, 127, 102, 105, 114, 101, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -127, 82, 80, 96, 32, 1, -111, 80, 80, 96, 64, 81, -128, -111, 3, -112, -95, 86, 91, 96, 0, 84, -127, 86, 91, -128, 96, 0, -127, -112, 85, 80, 97, 34, 34, 96, 1, 2, 97, 17, 17, 96, 64, 81, -128, -126, 96, 1, 2, 96, 0, 25, 22, -127, 82, 96, 32, 1, -111, 80, 80, 96, 64, 81, -128, -111, 3, -112, -95, 80, 86, 0, -95, 101, 98, 122, 122, 114, 48, 88, 32, -121, -35, 34, 93, 122, -19, 95, -92, -1, -29, 108, -128, -54, -55, -79, -52, 23, 125, -42, 54, 38, -119, -44, -87, 61, -57, -70, 101, -45, 77, 11, -73, 0, 41)
      val vmSettings = ContractSettings(0, false)
      //    val invoker = VMTest.createInvoker(Array.emptyByteArray)
      val invoker = VMTest.createInvoker(Array(96, -2, 71, -79, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 7, 119))
      val program = new Program(vmSettings, code, invoker)
      val vm = new VM(vmSettings, VMHook.EMPTY)
      vm.play(program)
      println(util.Arrays.toString(program.getResult.getHReturn))
    }

    {
      val code = Array[Byte](96, 96, 96, 64, 82, 96, 4, 54, 16, 97, 0, 98, 87, 96, 0, 53, 124, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -112, 4, 99, -1, -1, -1, -1, 22, -128, 99, 44, 4, 71, 121, 20, 97, 0, 103, 87, -128, 99, 69, 112, -108, -52, 20, 97, 0, -68, 87, -128, 99, 78, 112, -79, -36, 20, 97, 0, -47, 87, -128, 99, 96, -2, 71, -79, 20, 97, 0, -6, 87, 91, 96, 0, -128, -3, 91, 52, 21, 97, 0, 114, 87, 96, 0, -128, -3, 91, 97, 0, 122, 97, 1, 29, 86, 91, 96, 64, 81, -128, -126, 115, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 22, 115, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 22, -127, 82, 96, 32, 1, -111, 80, 80, 96, 64, 81, -128, -111, 3, -112, -13, 91, 52, 21, 97, 0, -57, 87, 96, 0, -128, -3, 91, 97, 0, -49, 97, 1, 37, 86, 91, 0, 91, 52, 21, 97, 0, -36, 87, 96, 0, -128, -3, 91, 97, 0, -28, 97, 1, -113, 86, 91, 96, 64, 81, -128, -126, -127, 82, 96, 32, 1, -111, 80, 80, 96, 64, 81, -128, -111, 3, -112, -13, 91, 52, 21, 97, 1, 5, 87, 96, 0, -128, -3, 91, 97, 1, 27, 96, 4, -128, -128, 53, -112, 96, 32, 1, -112, -111, -112, 80, 80, 97, 1, -107, 86, 91, 0, 91, 96, 0, 51, -112, 80, -112, 86, 91, 127, 5, -57, 102, -47, -59, -22, 111, 64, -81, -61, -116, -40, -30, 115, 8, -62, 54, -60, -110, -5, -49, -93, 43, 69, -115, 39, 85, -49, 118, -20, 30, 33, 96, 64, 81, -128, -128, 96, 32, 1, -126, -127, 3, -126, 82, 96, 4, -127, 82, 96, 32, 1, -128, 127, 102, 105, 114, 101, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -127, 82, 80, 96, 32, 1, -111, 80, 80, 96, 64, 81, -128, -111, 3, -112, -95, 86, 91, 96, 0, 84, -127, 86, 91, -128, 96, 0, -127, -112, 85, 80, 97, 34, 34, 96, 1, 2, 97, 17, 17, 96, 64, 81, -128, -126, 96, 1, 2, 96, 0, 25, 22, -127, 82, 96, 32, 1, -111, 80, 80, 96, 64, 81, -128, -111, 3, -112, -95, 80, 86, 0, -95, 101, 98, 122, 122, 114, 48, 88, 32, -121, -35, 34, 93, 122, -19, 95, -92, -1, -29, 108, -128, -54, -55, -79, -52, 23, 125, -42, 54, 38, -119, -44, -87, 61, -57, -70, 101, -45, 77, 11, -73, 0, 41)
      val vmSettings = ContractSettings(0, false)
      //    val invoker = VMTest.createInvoker(Array.emptyByteArray)
      val invoker = VMTest.createInvoker(Array(78, 112, -79, -36, 102, -123, -81, 15, -20, 51, -63, -61, 67, -13, -94, 10, 1, 2, -53, -48, -32, 114, 82, -26, -45, 94, 116, 37, -6, 52, -55, -93))
      val program = new Program(vmSettings, code, invoker)
      val vm = new VM(vmSettings, VMHook.EMPTY)
      vm.play(program)
      println(DataWord.of(program.getResult.getHReturn).value)
      assert(DataWord.of(program.getResult.getHReturn).intValue == 0x777)
    }
  }
}

object VMTest {
  private val dir = "VMTest"
  private val settings = DataBaseSettings(dir, true, 10)
  private val dataBase = new DataBase(settings)

  def createInvoker(data: Array[Byte]): ProgramInvoke = {

    val publicKey = PublicKey("022ac01a1ea9275241615ea6369c85b41e2016abc47485ec616c3c583f1b92a5c8")
    val contractAddress = Crypto.calcNewAddr(publicKey.pubKeyHash, BigInt(1).toByteArray)

    new ProgramInvokeImpl(
      DataWord.of(publicKey.pubKeyHash),
      DataWord.of(contractAddress),
      DataWord.ZERO,
      DataWord.ZERO,
      DataWord.ZERO,
      DataWord.of(Int.MaxValue),
      DataWord.ZERO,
      data,
      DataWord.ZERO,
      DataWord.ZERO,
      DataWord.ZERO,
      DataWord.ZERO,
      DataWord.ZERO,
      DataWord.ZERO,
      dataBase,
      dataBase,
      null)
  }

  @AfterClass
  def cleanUp: Unit = {
    try {
      dataBase.close()
      Directory(dir).deleteRecursively()
    } catch {
      case _: Throwable =>
    }
  }
}
