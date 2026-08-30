package freechips.rocketchip.rocket

/** NACC A-mode 特权级模型的架构常量。
  *
  * 模型仿 H 扩展的 `V` bit：
  *   - `A` 是一个**状态位**，不是特权级。`AS` = (A=1 ∧ priv=S)，`AU` = (A=1 ∧ priv=U)，
  *     二者组合才确定模式；`A` 之于 `AS` 正如 `V` 之于 `VS`。
  *   - `nacc_status` 是**一个物理寄存器、一个地址**，按当前 mode 施加读写掩码——
  *     与 `mstatus`/`sstatus` 共用一份存储、只是掩码视图不同是同一形状，区别仅在于
  *     标准把两个视图暴露成两个地址，这里收成一个。
  *   - A 世界的 trap CSR（`astvec` 等）**独立编号，不做 banking**。H 必须 banking 是
  *     因为它要跑未修改的 guest OS，透明性是被那个用例逼出来的；agent runtime 是专门
  *     写的、知道自己在 AS 上跑，透明性买不到东西，却要把 mux 摊到每一次 CSR 读写上。
  *   - 进入 A 世界一律落在 AS（不像 H 由 `SPP` 选 VS/VU），落点 PC 由 `nacc_aentry`
  *     强制。二者合起来使 Linux 决定「什么时候进」但决定不了「进到哪、以什么特权级进」。
  */
object NACCCSRs {
  /** 世界切换状态 + A 世界 trap 状态。M/S/AS 看到的字段由掩码区分。 */
  val nacc_status = 0x7c2
  /** `S → AS` 的入口 PC。M 可写；S 与 AS 均不可访问。 */
  val nacc_aentry = 0x7c3

  /** A 世界的 trap CSR，仅 `A=1` 或 `priv=M` 可访问。 */
  val astvec    = 0x5c0
  val asepc     = 0x5c1
  val ascause   = 0x5c2
  val astval    = 0x5c3
  val asscratch = 0x5c4

  /** A 世界 trap CSR 的集合，用于访问控制判定。 */
  val aTrapCSRs = Seq(astvec, asepc, ascause, astval, asscratch)
}

/** `nacc_status` 的位布局。
  *
  * 同时装两个世界的状态，与 `mstatus` 同时装 `MPP`/`MPIE` 与 `SPP`/`SPIE` 是同一形状。
  */
object NACCStatus {
  /** trap 进 M 时记录的 `A`。仅 M 可见可写。 */
  val MPA = 0
  /** trap 进 S 时记录的 `A`；`SRET` 时决定是否进 A 世界。M 与 S 可读写。 */
  val SPA = 1
  /** 当前是否处于 A 世界。全模式只读，是执行状态的镜像而非存储位。 */
  val A = 2
  /** A 世界 trap 前的特权级（0 = AU，1 = AS）。M 与 AS 可读写。 */
  val ASPP = 3
  /** A 世界的 previous interrupt enable。M 与 AS 可读写。 */
  val ASPIE = 4
  /** A 世界的 interrupt enable。M 与 AS 可读写。 */
  val ASIE = 5

  val Width = 6

  private def bit(i: Int): BigInt = BigInt(1) << i

  /** 实际有存储的位。`A` 是镜像，不占存储。 */
  val StoredMask: BigInt = bit(MPA) | bit(SPA) | bit(ASPP) | bit(ASPIE) | bit(ASIE)

  /** 世界切换相关字段：`MPA` / `SPA` / `A`。 */
  val WorldMask: BigInt = bit(MPA) | bit(SPA) | bit(A)
  /** A 世界的 trap 状态字段：`ASPP` / `ASPIE` / `ASIE`。 */
  val ATrapMask: BigInt = bit(ASPP) | bit(ASPIE) | bit(ASIE)

  /** 各 mode 的读掩码。掩掉的位读出 0。 */
  val ReadMaskM: BigInt = WorldMask | ATrapMask
  val ReadMaskS: BigInt = bit(SPA) | bit(A)
  val ReadMaskAS: BigInt = bit(A) | ATrapMask

  /** 各 mode 的写掩码。`A` 是只读镜像，任何 mode 都写不进。 */
  val WriteMaskM: BigInt = ReadMaskM & StoredMask
  val WriteMaskS: BigInt = ReadMaskS & StoredMask
  val WriteMaskAS: BigInt = ReadMaskAS & StoredMask
}
