package tw.nekomimi.nekogram.helpers;

import org.telegram.messenger.ChatObject;
import org.telegram.tgnet.TLRPC;
import tw.nekomimi.nekogram.NekoConfig;

public class ChatHelper {
    public static boolean isEffectivelyInChat(TLRPC.Chat chat) {
        if (chat == null) return false;
        if (!ChatObject.isNotInChat(chat)) return true;
        if (!NekoConfig.sendToDiscussWithoutJoin.Bool()) return false;
        if (chat.join_to_send) return false;
        return chat.megagroup && chat.has_link;
    }
}
