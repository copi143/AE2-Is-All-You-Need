package allyouneed.transformer

object Constants {

    // ===== AEKey =========================

    const val AE_KEY = "appeng/api/stacks/AEKey"
    const val AE_KEY_ASM = "appeng/api/stacks/AEKeyAsm"
    const val AE_KEY_INTERNER = "appeng/api/stacks/KeyInterner"

    const val ASM_EQUALS = $$"asm$equals"
    const val ASM_HASH = $$"asm$hashCode"
    const val ASM_DROP_SECONDARY = $$"asm$dropSecondary"

    const val DROP_SECONDARY = "dropSecondary"

    // ===== ResourceLocation =========================

    const val RESOURCE_LOCATION = "net/minecraft/resources/ResourceLocation"
    const val RESOURCE_LOCATION_INTERNER = "net/minecraft/resources/ResourceLocationInterner"

    // ===== Gson fast path =========================

    const val GSON_RTAF = "com/google/gson/internal/bind/ReflectiveTypeAdapterFactory"
    const val GSON_STREAMS = "com/google/gson/internal/Streams"
    const val GSON_PACKAGE = "com.google.gson.internal.bind."
    const val GSON_PACKAGE_ANCHOR = "com.google.gson.internal.bind.TypeAdapters"
    const val GSON_FAST_PATH_HOOK = "com/google/gson/internal/bind/GsonFastPath"

}
