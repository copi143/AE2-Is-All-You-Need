package allyouneed.client.integration.jei

import allyouneed.client.itemdetail.ItemDetails
import allyouneed.client.itemdetail.ItemDetailsScreen

/**
 * Block-details screen shown when JEI is the available integration. JEI does
 * not expose a public widget API for standalone screens, so it reuses the
 * vanilla renderer from [ItemDetailsScreen]. The JEI-specific parts (hovered
 * stack detection, runtime storage) live in [JeiRuntimeStore] and
 * [allyouneed.client.itemdetail.focus.ItemDetailsFocus].
 */
class JeiItemDetailsScreen(details: ItemDetails) : ItemDetailsScreen(details)
