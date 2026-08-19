package allyouneed.client.integration.jei

/**
 * Holds the JEI runtime (`mezz.jei.api.runtime.IJeiRuntime`) captured from
 * [mezz.jei.api.IModPlugin.onRuntimeAvailable]. Stored as [Any] so that the
 * item-details focus helper can reach it reflectively without JEI being a
 * hard dependency of the common module.
 */
object JeiRuntimeStore {
    @JvmStatic
    @Volatile
    var runtime: Any? = null
}
