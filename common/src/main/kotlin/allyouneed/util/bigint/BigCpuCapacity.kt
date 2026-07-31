package allyouneed.util.bigint

import java.math.BigInteger

interface BigCpuCapacity {
    fun getBigStorage(): BigInteger

    fun setBigStorage(bytes: BigInteger)

    fun isUnboundedCapacity(): Boolean

    fun setUnboundedCapacity(unbounded: Boolean)
}
