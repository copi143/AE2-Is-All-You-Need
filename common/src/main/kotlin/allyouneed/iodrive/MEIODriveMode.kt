package allyouneed.iodrive

enum class MEIODriveMode(val label: String) {
    PAUSED("Paused"),
    OUTPUT("Output (Transfer to Network)"),
    INPUT("Input (Transfer from Network)");

    fun next(): MEIODriveMode = when (this) {
        PAUSED -> OUTPUT
        OUTPUT -> INPUT
        INPUT -> PAUSED
    }
}
