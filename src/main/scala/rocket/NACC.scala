package freechips.rocketchip.rocket

import chisel3._
import chisel3.util.BitPat

object NACCInstructions {
  val ACALL = BitPat("b00000000000000000000000000001011")
  val ARET  = BitPat("b00010000001000000000000000001011")
}

object NACCState {
  val CidBits = 48
  val StateLow = 48
  val StateHigh = 49
  val PendingReturnBit = 50

  val Agent = 1
  val Linux = 2

  val StateMask = BigInt(3) << StateLow
  val PendingReturnMask = BigInt(1) << PendingReturnBit
  val AgentField = BigInt(Agent) << StateLow
  val LinuxField = BigInt(Linux) << StateLow

  /** 只有ACALL/ARET定义的两种canonical lifecycle状态属于机密执行期。 */
  def confidentialActive(value: UInt): Bool = {
    val cidValid = value(CidBits - 1, 0).orR
    val state = value(StateHigh, StateLow)
    val pending = value(PendingReturnBit)
    cidValid && ((state === Agent.U && !pending) || (state === Linux.U && pending))
  }
}

object NACCBitmapTag {
  val Width = 2

  val Normal = 0
  val RootL0 = 1
  val PrivateData = 2
  val PrivateCopyPending = 3
}
