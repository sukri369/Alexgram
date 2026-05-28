package xyz.nextalone.nagram.helper

import android.content.SharedPreferences
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.ui.LaunchActivity
import tw.nekomimi.nekogram.ui.AnnouncementAlert
import xyz.nextalone.nagram.NaConfig

object AnnouncementHelper {
    private var checkedThisSession = false

    @JvmStatic
    fun checkAnnouncement(activity: LaunchActivity?) {
        if (checkedThisSession || activity == null) {
            return
        }
        if (!NaConfig.getImportantAnnouncementFromAlexgram.Bool()) {
            return
        }
        val currentAccount = UserConfig.selectedAccount
        if (!UserConfig.getInstance(currentAccount).isClientActivated) {
            return
        }

        checkedThisSession = true

        val resolveReq = TLRPC.TL_contacts_resolveUsername()
        resolveReq.username = "AlexgramNotices"

        ConnectionsManager.getInstance(currentAccount).sendRequest(resolveReq, { res1, err1 ->
            AndroidUtilities.runOnUIThread {
                if (err1 != null || res1 !is TLRPC.TL_contacts_resolvedPeer) {
                    return@runOnUIThread
                }
                if (res1.chats.isEmpty()) {
                    return@runOnUIThread
                }
                val chat = res1.chats[0]

                val mc = MessagesController.getInstance(currentAccount)
                mc.putUsers(res1.users, false)
                mc.putChats(res1.chats, false)
                MessagesStorage.getInstance(currentAccount).putUsersAndChats(res1.users, res1.chats, false, true)

                val inputPeer = MessagesController.getInputPeer(chat) ?: return@runOnUIThread

                val getHistoryReq = TLRPC.TL_messages_getHistory()
                getHistoryReq.peer = inputPeer
                getHistoryReq.limit = 1

                ConnectionsManager.getInstance(currentAccount).sendRequest(getHistoryReq, { res2, err2 ->
                    AndroidUtilities.runOnUIThread {
                        if (err2 != null || res2 !is TLRPC.messages_Messages) {
                            return@runOnUIThread
                        }
                        if (res2.messages.isEmpty()) {
                            return@runOnUIThread
                        }
                        val message = res2.messages[0] ?: return@runOnUIThread

                        val prefs = NaConfig.getPreferences()
                        val lastShownId = prefs.getInt("last_announcement_id", 0)
                        if (message.id > lastShownId) {
                            try {
                                val alert = AnnouncementAlert(activity, message, chat)
                                alert.show()
                                prefs.edit().putInt("last_announcement_id", message.id).apply()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                })
            }
        })
    }
}
