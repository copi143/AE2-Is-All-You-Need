package allyouneed.async

/**
 * Implemented by every block that represents an async synthesis block kind, whether it is a plain
 * structural block or a GT machine block. Lets the structure detectors read a block's role from the
 * world without knowing which registration path produced it.
 */
interface IAsyncKindBlock {
    val kind: AsyncBlockKind
}
