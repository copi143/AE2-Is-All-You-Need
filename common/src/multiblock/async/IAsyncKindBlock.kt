package allyouneed.multiblock.async

/**
 * 所有代表 async 合成方块种类的方块都实现该接口，无论是普通结构方块还是 GT
 * 机器方块。这让结构检测器可以只凭方块在世界的种类（[kind]）来识别角色，
 * 而无需关心它是由哪条注册路径产生的。
 *
 * Implemented by every block that represents an async synthesis block kind, whether it is a plain
 * structural block or a GT machine block. Lets the structure detectors read a block's role from the
 * world without knowing which registration path produced it.
 */
interface IAsyncKindBlock {
    val kind: AsyncBlockKind
}
