package freechips.rocketchip.rocket

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
}
