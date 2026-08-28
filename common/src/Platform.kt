package allyouneed

import allyouneed.util.interfaces.PlatformHelper

object Platform : PlatformHelper by PlatformHelper.load()
