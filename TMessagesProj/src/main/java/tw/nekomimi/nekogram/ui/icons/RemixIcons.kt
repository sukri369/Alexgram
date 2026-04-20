package tw.nekomimi.nekogram.ui.icons

import org.telegram.messenger.R

class RemixIcons {
    companion object {
        val remixIcons = mutableListOf<Pair<Int, Int>>()

        init {
            remixIcons.add(R.drawable.msg_calls_regular to R.drawable.remix_voiceprint)
            remixIcons.add(R.drawable.magic_stick_solar to R.drawable.remix_sparkling)
            remixIcons.add(R.drawable.msg_info_solar to R.drawable.remix_summary)
            remixIcons.add(R.drawable.msg_translate to R.drawable.remix_translate)
        }

        fun getConversion(icon: Int): Int {
            for (pair in remixIcons) {
                if (pair.first == icon) {
                    return pair.second
                }
            }
            return icon
        }
    }
}
