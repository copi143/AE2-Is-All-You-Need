package allyouneed.api

import appeng.api.stacks.AEKey

interface KeyLocations {
    fun getLocations(key: AEKey): List<KeyLocation>

    fun replaceLocations(locations: Map<AEKey, @JvmSuppressWildcards List<KeyLocation>>)

    fun addLocations(locations: Map<AEKey, @JvmSuppressWildcards List<KeyLocation>>)

    fun copyLocations(): Map<AEKey, List<KeyLocation>>
}
