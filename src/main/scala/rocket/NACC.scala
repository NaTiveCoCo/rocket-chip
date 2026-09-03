package freechips.rocketchip.rocket

/** NACC A-mode 特权级模型的架构常量。
  *
  * 模型仿 H 扩展的 `V` bit：
  *   - `A` 是一个**状态位**，不是特权级。`AS` = (A=1 ∧ priv=S)，`AU` = (A=1 ∧ priv=U)，
  *     二者组合才确定模式；`A` 之于 `AS` 正如 `V` 之于 `VS`。
  *   - `asstatus` 是**一个物理寄存器、一个地址**，按当前 mode 施加读写掩码——
  *     与 `mstatus`/`sstatus` 共用一份存储、只是掩码视图不同是同一形状，区别仅在于
  *     标准把两个视图暴露成两个地址，这里收成一个。
  *   - A 世界的 trap CSR（`astvec`/`asepc`/...）**独立编号，不做 banking**。H 必须
  *     banking 是因为它要跑未修改的 guest OS，透明性是被那个用例逼出来的；agent
  *     runtime 是专门写的、知道自己在 AS 上跑，透明性买不到东西，却要把 mux 摊到
  *     每一次 CSR 读写上。
  *   - `SRET`、`ASRET`、`MRET` 分别使用 `sepc`+`SPA`、`asepc`+`ASPA`、
  *     `mepc`+`MPA` 恢复 previous world，并沿用各自的 standard previous privilege。
  *
  * 两种访问控制机制服务两种需要：`asstatus` 按**字段**掩码（Linux 必须能写 `SPA`
  * 才能调度 agent，而 `MPA` 必须对它不可见）；`as*` trap CSR 按**整个寄存器**控制
  * （`A=1` 或 `priv=M`，Linux 对它们没有任何正当用途）。
  */
object NACCCSRs {
  /** 世界切换状态 + A 世界 trap 状态。M/S/AS 看到的字段由掩码区分。 */
  val asstatus = 0x7c2

  /** A 世界的 trap CSR，仅 AS 或 `priv=M` 可访问。`asepc` 只保存 A 世界内部 trap PC；
    * `AS ECALL` 进入 S 时改写的是 `sepc`，并保持 `asepc` 不变。
    */
  val astvec    = 0x5c0
  val asepc     = 0x5c1
  val ascause   = 0x5c2
  val astval    = 0x5c3
  val asscratch = 0x5c4
  val asip      = 0x5c5
  val asie      = 0x5c6

  /** M-mode 独占的 A-side exception/interrupt delegation bitmap。 */
  val aedeleg = 0x7c3
  val aideleg = 0x7c4

  /** 旧 prototype CSR 迁入 machine custom RW 区。 */
  val sagent            = 0x7d0
  val eagent            = 0x7d1
  val bitmapStorageBase = 0x7d2
  val bitmapTargetStart = 0x7d3
  val bitmapTargetEnd   = 0x7d4

  /** A 世界 trap CSR 的集合，用于访问控制判定。 */
  val aTrapCSRs = Seq(astvec, asepc, ascause, astval, asscratch, asip, asie)
}

object NACCCauses {
  /** custom synchronous exception cause：仅 AS 执行 ECALL 时产生。 */
  val AS_ECALL = 24
}

/** `asstatus` 的位布局。
  *
  * 同时装两个层级的状态，与 `mstatus` 同时装 `MPP`/`MPIE` 与 `SPP`/`SPIE` 是同一形状。
  */
object NACCStatus {
  /** trap 进 M 时记录的 `A`。仅 M 可见可写。 */
  val MPA = 0
  /** trap 进 S 时记录的 `A`；`SRET` 时决定是否进 A 世界。M 与 S 可读写。 */
  val SPA = 1
  /** trap 进 AS 时记录的 world。M 与 AS 可读写。 */
  val ASPA = 2
  /** A 世界 trap 前的特权级（0 = AU，1 = AS）。M 与 AS 可读写。 */
  val ASPP = 3
  /** A 世界的 previous interrupt enable。M 与 AS 可读写。 */
  val ASPIE = 4
  /** A 世界的 interrupt enable。M 与 AS 可读写。 */
  val ASIE = 5

  val Width = 6

  /** 仅在硬件 bundle 内传递，不属于 `asstatus` 软件可见位。 */
  val InternalCurrentA = 6
  val InternalDataA = 7

  private def bit(i: Int): BigInt = BigInt(1) << i

  /** 实际有存储的位；hidden `A` 不占任何软件可见位。 */
  val StoredMask: BigInt = bit(MPA) | bit(SPA) | bit(ASPA) | bit(ASPP) | bit(ASPIE) | bit(ASIE)

  /** 世界切换相关字段。 */
  val WorldMask: BigInt = bit(MPA) | bit(SPA) | bit(ASPA)
  /** A 世界自己的 trap 状态字段。 */
  val ATrapMask: BigInt = bit(ASPA) | bit(ASPP) | bit(ASPIE) | bit(ASIE)

  /** 各 mode 的读掩码。掩掉的位读出 0。 */
  val ReadMaskM: BigInt = WorldMask | ATrapMask
  val ReadMaskS: BigInt = bit(SPA)
  val ReadMaskAS: BigInt = ATrapMask

  /** 各 mode 的写掩码。hidden `A` 不在 CSR 中，任何 mode 都无法读写。 */
  val WriteMaskM: BigInt = ReadMaskM & StoredMask
  val WriteMaskS: BigInt = ReadMaskS & StoredMask
  val WriteMaskAS: BigInt = ReadMaskAS & StoredMask
}

/** PFN bitmap 的 raw tag 编码，每个 4 KiB 物理页 2 bit。
  *
  * tag 是 final physical PFN 的属性，与虚拟地址、PTE permission 和 requester 都无关：
  * 同一个物理页无论由哪条 VA、哪种 PTE 权限映射，读到的 raw tag 必须相同，只有可信
  * AS/M 对 bitmap backing memory 的普通 data 写入能改变它。因此 TLB entry 里缓存的是 **raw
  * tag 本身**，而不是某次 privilege/state 下算出的最终权限——`A` 位变化后下一次 hit
  * 用 live 状态重新计算，不需要仅为世界切换执行 `SFENCE.VMA`。
  *
  * 宽度保持 2 bit 而不加宽：三个稳定标签和一个 COW pending 恰好装下，而加宽到 1 byte 会把
  * 一条 64 byte metadata cache line 的覆盖面从 1 MiB 缩到 256 KiB。代价是两条 monitor
  * 的软件义务——由可信 AS/M 串行化 tag 更新（4 个 PFN 共用一个 byte，future multicore
  * 并发 read-modify-write 会丢更新）。软件可以按部署约定在 bitmap 数组之外记录格式版本，
  * 但硬件不要求或解释该版本。
  */
object NACCBitmapTag {
  val Width = 2

  /** 公共/共享内存。target range 外「不施加额外限制」也用这个编码表示，但 target
    * range 内的 `Normal` 必须来自真实的 bitmap read，不能用默认零值冒充。 */
  val Normal = 0
  /** 半分的根页表：Linux S 只可写当前根页的内核半（Sv39 顶层 entry 256..511）；
    * AS 可读写整张根页并管理对应软件对象，M 保留可信兜底权限。 */
  val RootL0 = 1
  /** Agent 私有，通常用于下级页表页、私有数据与 AS 软件保存的机密执行上下文。 */
  val PrivateData = 2
  /** 仅用于 COW 的 pending 状态；不是通用创建、回收或迁移生命周期。 */
  val PrivateCopyPending = 3
}
