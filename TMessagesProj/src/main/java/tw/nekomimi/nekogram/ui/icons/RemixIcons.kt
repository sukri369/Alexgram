package tw.nekomimi.nekogram.ui.icons

import org.telegram.messenger.R

class RemixIcons {
    companion object {
        val remixIcons = mutableListOf<Pair<Int, Int>>()

        init {
            remixIcons.add(R.drawable.msg_calls_regular to R.drawable.remix_voiceprint)
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
