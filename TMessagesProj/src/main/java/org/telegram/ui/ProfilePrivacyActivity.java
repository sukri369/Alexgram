package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import java.util.ArrayList;
import java.util.HashMap;
import tw.nekomimi.nekogram.NekoConfig;

public class ProfilePrivacyActivity extends UniversalFragment {

    private final TLRPC.User user;
    private final int account;
    private final HashMap<Integer, Integer> currentStates = new HashMap<>();
    private boolean loading = true;
    private int pendingFetches = 15;

    public static final int STATE_DEFAULT = 0;
    public static final int STATE_ALWAYS  = 1;
    public static final int STATE_NEVER   = 2;

    public static final String[] PRIVACY_LABELS = {
        "Last Seen & Online",
        "Invites",
        "Call",
        "Peer-to-Peer Calls",
        "Profile Photos",
        "Forwarded Messages",
        "Phone Number",
        "Added by Phone",
        "Voice Messages",
        "Bio",
        "Messages",
        "Birthday",
        "Gifts",
        "Paid Messages",
        "Saved Music"
    };

    public static final String[] STATE_LABELS = {
        "Default",
        "Enabled",
        "Disabled"
    };

    public ProfilePrivacyActivity(TLRPC.User user) {
        this.user = user;
        this.account = currentAccount;
    }

    @Override
    public boolean onFragmentCreate() {
        fetchAllPrivacy();
        return super.onFragmentCreate();
    }

    private void fetchAllPrivacy() {
        for (int i = 0; i < 15; i++) {
            fetchPrivacy(i);
        }
    }

    private TLRPC.InputPrivacyKey getInputPrivacyKey(int type) {
        switch (type) {
            case 0:  return new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
            case 1:  return new TLRPC.TL_inputPrivacyKeyChatInvite();
            case 2:  return new TLRPC.TL_inputPrivacyKeyPhoneCall();
            case 3:  return new TLRPC.TL_inputPrivacyKeyPhoneP2P();
            case 4:  return new TLRPC.TL_inputPrivacyKeyProfilePhoto();
            case 5:  return new TLRPC.TL_inputPrivacyKeyForwards();
            case 6:  return new TLRPC.TL_inputPrivacyKeyPhoneNumber();
            case 7:  return new TLRPC.TL_inputPrivacyKeyAddedByPhone();
            case 8:  return new TLRPC.TL_inputPrivacyKeyVoiceMessages();
            case 9:  return new TLRPC.TL_inputPrivacyKeyAbout();
            case 10: return new TLRPC.TL_inputPrivacyKeyNoPaidMessages();
            case 11: return new TLRPC.TL_inputPrivacyKeyBirthday();
            case 12: return new TLRPC.TL_inputPrivacyKeyStarGiftsAutoSave();
            case 13: return new TLRPC.TL_inputPrivacyKeyNoPaidMessages();
            case 14: return new TLRPC.TL_inputPrivacyKeySavedMusic();
            default: return null;
        }
    }

    private void fetchPrivacy(final int privacyType) {
        TLRPC.InputPrivacyKey key = getInputPrivacyKey(privacyType);
        if (key == null) return;

        TL_account.getPrivacy req = new TL_account.getPrivacy();
        req.key = key;

        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null && response instanceof TL_account.privacyRules) {
                TL_account.privacyRules rules = (TL_account.privacyRules) response;
                getMessagesController().putUsers(rules.users, false);
                getMessagesController().putChats(rules.chats, false);
                getContactsController().setPrivacyRules(rules.rules, privacyType);

                int state = STATE_DEFAULT;
                for (int i = 0; i < rules.rules.size(); i++) {
                    TLRPC.PrivacyRule rule = rules.rules.get(i);
                    if (rule instanceof TLRPC.TL_privacyValueAllowUsers) {
                        if (((TLRPC.TL_privacyValueAllowUsers) rule).users.contains(user.id)) {
                            state = STATE_ALWAYS;
                            break;
                        }
                    } else if (rule instanceof TLRPC.TL_privacyValueDisallowUsers) {
                        if (((TLRPC.TL_privacyValueDisallowUsers) rule).users.contains(user.id)) {
                            state = STATE_NEVER;
                            break;
                        }
                    }
                }
                currentStates.put(privacyType, state);
            }
            pendingFetches--;
            if (pendingFetches <= 0) {
                loading = false;
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }));
    }

    @Override
    protected CharSequence getTitle() {
        return "Privacy";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (getContext() == null) return;

        ProfileSearchCell userCell = new ProfileSearchCell(getContext());
        userCell.setData(user, null, null, null, false, false);
        items.add(UItem.asCustom(userCell));

        boolean isContact = getContactsController().contactsDict.get(user.id) != null;
        if (isContact) {
            items.add(UItem.asButton(100, R.drawable.msg_delete, LocaleController.getString("DeleteContact", R.string.DeleteContact)).red());
            String name = UserObject.getFirstName(user);
            items.add(UItem.asShadow("Also " + name + " added you to their contacts."));
        }

        if (loading) {
            items.add(UItem.asHeader("What can they see?"));
            for (int i = 0; i < 6; i++) {
                items.add(UItem.asFlicker(6));
            }
            items.add(UItem.asHeader("What can they do?"));
            for (int i = 0; i < 5; i++) {
                items.add(UItem.asFlicker(6));
            }
            return;
        }

        items.add(UItem.asHeader("What can they see?"));
        int[] seeTypes = {0, 4, 6, 9, 11, 14};
        for (int type : seeTypes) {
            addPrivacyItem(items, type);
        }

        items.add(UItem.asHeader("What can they do?"));
        int[] doTypes = {1, 2, 3, 5, 8};
        for (int type : doTypes) {
            addPrivacyItem(items, type);
        }
        items.add(UItem.asShadow(null));
    }

    private void addPrivacyItem(ArrayList<UItem> items, int type) {
        String label = PRIVACY_LABELS[type];
        Integer stateVal = currentStates.get(type);
        int state = stateVal != null ? stateVal : STATE_DEFAULT;

        ArrayList<TLRPC.PrivacyRule> rules = getContactsController().getPrivacyRules(type);
        boolean effective = isAllowedByRules(user, rules, state);

        String subtext = STATE_LABELS[state];
        int uitemId = type == 0 ? 1000 : type;

        UItem item = UItem.asButtonCheck(uitemId, label, subtext);
        item.checked = effective;
        items.add(item);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int actualId = item.id == 1000 ? 0 : item.id;

        if (actualId >= 0 && actualId < 15) {
            Integer stateVal = currentStates.get(actualId);
            int currentState = stateVal != null ? stateVal : STATE_DEFAULT;

            ArrayList<TLRPC.PrivacyRule> rules = getContactsController().getPrivacyRules(actualId);
            boolean globalAllows = isAllowedByRules(user, rules, STATE_DEFAULT);

            int newState;
            if (globalAllows) {
                newState = currentState == STATE_NEVER ? STATE_DEFAULT : STATE_NEVER;
            } else {
                newState = currentState == STATE_ALWAYS ? STATE_DEFAULT : STATE_ALWAYS;
            }

            setPrivacyState(actualId, newState);
            return;
        }

        if (item.id == 100) {
            deleteContact();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void deleteContact() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(LocaleController.getString("DeleteContact", R.string.DeleteContact));
        builder.setMessage(LocaleController.getString("AreYouSureDeleteContact", R.string.AreYouSureDeleteContact));
        builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (d, w) -> {
            ArrayList<TLRPC.User> arrayList = new ArrayList<>();
            arrayList.add(user);
            getContactsController().deleteContact(arrayList, true);
            finishFragment();
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(getThemedColor(Theme.key_text_RedBold));
        }
    }

    private void setPrivacyState(int privacyType, int newState) {
        currentStates.put(privacyType, newState);
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }

        updateUserPrivacy(privacyType, newState);
    }

    private boolean isAllowedByRules(TLRPC.User user, ArrayList<TLRPC.PrivacyRule> rules, int state) {
        if (state == STATE_ALWAYS) return true;
        if (state == STATE_NEVER) return false;
        if (rules == null) return true;

        for (int i = 0; i < rules.size(); i++) {
            TLRPC.PrivacyRule rule = rules.get(i);
            if (rule instanceof TLRPC.TL_privacyValueAllowUsers) {
                if (((TLRPC.TL_privacyValueAllowUsers) rule).users.contains(user.id)) {
                    return true;
                }
            } else if (rule instanceof TLRPC.TL_privacyValueDisallowUsers) {
                if (((TLRPC.TL_privacyValueDisallowUsers) rule).users.contains(user.id)) {
                    return false;
                }
            }
        }

        for (int i = 0; i < rules.size(); i++) {
            TLRPC.PrivacyRule rule = rules.get(i);
            if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
                return true;
            }
            if (rule instanceof TLRPC.TL_privacyValueDisallowAll) {
                return false;
            }
            if (rule instanceof TLRPC.TL_privacyValueAllowContacts) {
                return user.contact;
            }
            if (rule instanceof TLRPC.TL_privacyValueDisallowContacts) {
                return !user.contact;
            }
        }

        return true;
    }

    private void updateUserPrivacy(final int privacyType, final int newState) {
        ArrayList<TLRPC.PrivacyRule> rules = getContactsController().getPrivacyRules(privacyType);
        ArrayList<TLRPC.InputPrivacyRule> inputRules = new ArrayList<>();

        TLRPC.InputPrivacyKey inputKey = getInputPrivacyKey(privacyType);
        if (inputKey == null) return;

        TLRPC.InputPrivacyRule foundAllow = null;
        TLRPC.InputPrivacyRule foundDisallow = null;

        if (rules != null) {
            for (int i = 0; i < rules.size(); i++) {
                TLRPC.PrivacyRule rule = rules.get(i);
                TLRPC.InputPrivacyRule inputRule = toInputRule(rule);
                if (inputRule == null) continue;

                if (inputRule instanceof TLRPC.TL_inputPrivacyValueAllowUsers) {
                    if (foundAllow == null) {
                        foundAllow = new TLRPC.TL_inputPrivacyValueAllowUsers();
                    }
                    for (int j = 0; j < ((TLRPC.TL_inputPrivacyValueAllowUsers) inputRule).users.size(); j++) {
                        TLRPC.InputUser curU = ((TLRPC.TL_inputPrivacyValueAllowUsers) inputRule).users.get(j);
                        if (curU.user_id != user.id) {
                            ((TLRPC.TL_inputPrivacyValueAllowUsers) foundAllow).users.add(curU);
                        }
                    }
                } else if (inputRule instanceof TLRPC.TL_inputPrivacyValueDisallowUsers) {
                    if (foundDisallow == null) {
                        foundDisallow = new TLRPC.TL_inputPrivacyValueDisallowUsers();
                    }
                    for (int j = 0; j < ((TLRPC.TL_inputPrivacyValueDisallowUsers) inputRule).users.size(); j++) {
                        TLRPC.InputUser curU = ((TLRPC.TL_inputPrivacyValueDisallowUsers) inputRule).users.get(j);
                        if (curU.user_id != user.id) {
                            ((TLRPC.TL_inputPrivacyValueDisallowUsers) foundDisallow).users.add(curU);
                        }
                    }
                } else {
                    inputRules.add(inputRule);
                }
            }
        }

        TLRPC.InputUser inputUser = getMessagesController().getInputUser(user);

        if (newState == STATE_ALWAYS) {
            if (foundAllow == null) {
                foundAllow = new TLRPC.TL_inputPrivacyValueAllowUsers();
            }
            ((TLRPC.TL_inputPrivacyValueAllowUsers) foundAllow).users.add(inputUser);
        } else if (newState == STATE_NEVER) {
            if (foundDisallow == null) {
                foundDisallow = new TLRPC.TL_inputPrivacyValueDisallowUsers();
            }
            ((TLRPC.TL_inputPrivacyValueDisallowUsers) foundDisallow).users.add(inputUser);
        }

        if (foundAllow != null && !((TLRPC.TL_inputPrivacyValueAllowUsers) foundAllow).users.isEmpty()) {
            inputRules.add(0, foundAllow);
        }
        if (foundDisallow != null && !((TLRPC.TL_inputPrivacyValueDisallowUsers) foundDisallow).users.isEmpty()) {
            inputRules.add(0, foundDisallow);
        }

        TL_account.setPrivacy req = new TL_account.setPrivacy();
        req.key = inputKey;
        req.rules = inputRules;

        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null && response instanceof TL_account.privacyRules) {
                TL_account.privacyRules res = (TL_account.privacyRules) response;
                getContactsController().setPrivacyRules(res.rules, privacyType);
            } else {
                // Fetch again to restore
                fetchPrivacy(privacyType);
            }
        }));
    }

    private TLRPC.InputPrivacyRule toInputRule(TLRPC.PrivacyRule rule) {
        if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
            return new TLRPC.TL_inputPrivacyValueAllowAll();
        }
        if (rule instanceof TLRPC.TL_privacyValueAllowContacts) {
            return new TLRPC.TL_inputPrivacyValueAllowContacts();
        }
        if (rule instanceof TLRPC.TL_privacyValueDisallowAll) {
            return new TLRPC.TL_inputPrivacyValueDisallowAll();
        }
        if (rule instanceof TLRPC.TL_privacyValueDisallowContacts) {
            return new TLRPC.TL_inputPrivacyValueDisallowContacts();
        }
        if (rule instanceof TLRPC.TL_privacyValueAllowUsers) {
            TLRPC.TL_inputPrivacyValueAllowUsers res = new TLRPC.TL_inputPrivacyValueAllowUsers();
            for (int i = 0; i < ((TLRPC.TL_privacyValueAllowUsers) rule).users.size(); i++) {
                TLRPC.User u = getMessagesController().getUser(((TLRPC.TL_privacyValueAllowUsers) rule).users.get(i));
                if (u != null) {
                    res.users.add(getMessagesController().getInputUser(u));
                }
            }
            return res;
        }
        if (rule instanceof TLRPC.TL_privacyValueDisallowUsers) {
            TLRPC.TL_inputPrivacyValueDisallowUsers res = new TLRPC.TL_inputPrivacyValueDisallowUsers();
            for (int i = 0; i < ((TLRPC.TL_privacyValueDisallowUsers) rule).users.size(); i++) {
                TLRPC.User u = getMessagesController().getUser(((TLRPC.TL_privacyValueDisallowUsers) rule).users.get(i));
                if (u != null) {
                    res.users.add(getMessagesController().getInputUser(u));
                }
            }
            return res;
        }
        if (rule instanceof TLRPC.TL_privacyValueAllowChatParticipants) {
            TLRPC.TL_inputPrivacyValueAllowChatParticipants res = new TLRPC.TL_inputPrivacyValueAllowChatParticipants();
            for (int i = 0; i < ((TLRPC.TL_privacyValueAllowChatParticipants) rule).chats.size(); i++) {
                res.chats.add(((TLRPC.TL_privacyValueAllowChatParticipants) rule).chats.get(i));
            }
            return res;
        }
        if (rule instanceof TLRPC.TL_privacyValueDisallowChatParticipants) {
            TLRPC.TL_inputPrivacyValueDisallowChatParticipants res = new TLRPC.TL_inputPrivacyValueDisallowChatParticipants();
            for (int i = 0; i < ((TLRPC.TL_privacyValueDisallowChatParticipants) rule).chats.size(); i++) {
                res.chats.add(((TLRPC.TL_privacyValueDisallowChatParticipants) rule).chats.get(i));
            }
            return res;
        }
        return null;
    }
}
