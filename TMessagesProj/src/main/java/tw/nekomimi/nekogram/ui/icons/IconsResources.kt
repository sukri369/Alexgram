package tw.nekomimi.nekogram.ui.icons

import tw.nekomimi.nekogram.ExtraConfig

class IconsResources {
    companion object {
        @JvmStatic
        fun getConversion(icon: Int): Int {
            return when (ExtraConfig.iconReplacement) {
                1 -> SolarIcons.getConversion(icon)
                2 -> RemixIcons.getConversion(icon)
                else -> icon
            }
        }
    }
}
