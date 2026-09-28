package allyouneed.util

import net.minecraft.server.MinecraftServer

abstract class EntryPoint {
    /**
     * 尽可能在所有模组加载之前执行，不强制满足但必须早于本模组的初始化
     */
    protected open fun doBeforeAllMods() {}

    fun beforeAllMods() {
        debugLogger.info("${this::class.simpleName}::beforeAllMods begin")
        this.doBeforeAllMods()
        debugLogger.info("${this::class.simpleName}::beforeAllMods end")
    }

    /**
     * 尽可能在所有模组加载之后执行，不强制满足但必须晚于本模组的初始化
     */
    protected open fun doAfterAllMods() {}

    fun afterAllMods() {
        debugLogger.info("${this::class.simpleName}::afterAllMods begin")
        this.doAfterAllMods()
        debugLogger.info("${this::class.simpleName}::afterAllMods end")
    }

    protected open fun doInit() {}

    fun init() {
        debugLogger.info("${this::class.simpleName}::init begin")
        this.doInit()
        debugLogger.info("${this::class.simpleName}::init end")
    }

    protected open fun doServerStarting(server: MinecraftServer) {}

    fun serverStarting(server: MinecraftServer) {
        debugLogger.info("${this::class.simpleName}::serverStarting begin")
        this.doServerStarting(server)
        debugLogger.info("${this::class.simpleName}::serverStarting end")
    }

    protected open fun doServerStarted(server: MinecraftServer) {}

    fun serverStarted(server: MinecraftServer) {
        debugLogger.info("${this::class.simpleName}::serverStarted begin")
        this.doServerStarted(server)
        debugLogger.info("${this::class.simpleName}::serverStarted end")
    }

    protected open fun doServerStopping(server: MinecraftServer) {}

    fun serverStopping(server: MinecraftServer) {
        debugLogger.info("${this::class.simpleName}::serverStopping begin")
        this.doServerStopping(server)
        debugLogger.info("${this::class.simpleName}::serverStopping end")
    }

    protected open fun doServerStopped(server: MinecraftServer) {}

    fun serverStopped(server: MinecraftServer) {
        debugLogger.info("${this::class.simpleName}::serverStopped begin")
        this.doServerStopped(server)
        debugLogger.info("${this::class.simpleName}::serverStopped end")
    }
}
