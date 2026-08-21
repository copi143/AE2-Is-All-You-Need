package allyouneed.parts.logger

import appeng.api.networking.IGridService

interface INetworkLogService : IGridService {
    fun append(entry: NetworkLogEntry)
    fun isConflicted(): Boolean
    fun loggerCount(): Int
}
