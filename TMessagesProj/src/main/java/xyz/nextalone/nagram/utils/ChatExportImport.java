package xyz.nextalone.nagram.utils;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Base64;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import tw.nekomimi.nekogram.helpers.MessageHelper;

public class ChatExportImport {

    public static void exportChat(final Context context, final ArrayList<MessageObject> messages, final String title) {
        if (messages == null || messages.isEmpty()) {
            Toast.makeText(context, "No messages to export", Toast.LENGTH_SHORT).show();
            return;
        }

        org.telegram.ui.ActionBar.BottomSheet.Builder builder = new org.telegram.ui.ActionBar.BottomSheet.Builder(context);
        builder.setTitle("Chat Export Options", true);
        
        CharSequence[] items = new CharSequence[]{"Export as JSON", "Export as HTML with Media"};
        int[] icons = new int[]{org.telegram.messenger.R.drawable.msg_filehq_solar, org.telegram.messenger.R.drawable.msg_media_solar};
        
        builder.setItems(items, icons, (dialog, which) -> {
            if (which == 0) {
                performExportJson(context, messages, title);
            } else {
                performExportHtml(context, messages, title);
            }
        });
        
        builder.show();
    }

    private static void performExportJson(Context context, ArrayList<MessageObject> messages, String title) {
        final org.telegram.ui.ActionBar.AlertDialog progressDialog = new org.telegram.ui.ActionBar.AlertDialog(context, 3);
        progressDialog.setMessage("Preparing JSON export...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        Utilities.globalQueue.postRunnable(() -> {
            try {
                JSONArray jsonArray = new JSONArray();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

            for (MessageObject msg : messages) {
                if (msg == null) continue;
                JSONObject obj = new JSONObject();
                obj.put("id", msg.getId());
                obj.put("date", sdf.format(new Date(msg.messageOwner.date * 1000L)));

                String senderName = "Unknown";
                long senderId = msg.getSenderId();
                if (senderId > 0) {
                    TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(senderId);
                    if (user != null) senderName = org.telegram.messenger.UserObject.getUserName(user);
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-senderId);
                    if (chat != null) senderName = chat.title;
                }

                obj.put("sender", senderName);
                obj.put("message", msg.messageOwner.message);
                if (msg.isReply()) {
                    obj.put("reply_to_msg_id", msg.getReplyMsgId());
                }
                jsonArray.put(obj);
            }

            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AlexgramExports");
            if (!dir.exists()) dir.mkdirs();

            String fileName = "ChatExport_" + title.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis() + ".json";
            File file = new File(dir, fileName);

                FileOutputStream fos = new FileOutputStream(file);
                fos.write(jsonArray.toString(4).getBytes());
                fos.close();

                final File finalFile = file;
                AndroidUtilities.runOnUIThread(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    Toast.makeText(context, "Exported to " + finalFile.getAbsolutePath(), Toast.LENGTH_LONG).show();

                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("application/json");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(context, context.getPackageName() + ".provider", finalFile));
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(Intent.createChooser(shareIntent, "Share Export"));
                });

            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(context, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private static void performExportHtml(final Context context, final ArrayList<MessageObject> messages, final String title) {
        if (messages == null || messages.isEmpty()) return;

        final long dialogId = messages.get(0).getDialogId();
        final int currentAccount = UserConfig.selectedAccount;
        
        final org.telegram.ui.ActionBar.AlertDialog progressDialog = new org.telegram.ui.ActionBar.AlertDialog(context, 3);
        progressDialog.setMessage("Initializing full export...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        final ArrayList<MessageObject> fullHistory = new ArrayList<>();
        final HashMap<Integer, MessageObject> uniqueMessages = new HashMap<>();

        final NotificationCenterDelegate delegate = new NotificationCenterDelegate() {
            @Override
            public void didReceivedNotification(int id, int account, Object... args) {
                if (id == NotificationCenter.messagesDidLoad) {
                    long did = (Long) args[0];
                    if (did == dialogId) {
                        ArrayList<MessageObject> loaded = (ArrayList<MessageObject>) args[2];
                        boolean isEnd = (loaded == null || loaded.size() < 100);

                        if (loaded != null) {
                            for (MessageObject msg : loaded) {
                                if (!uniqueMessages.containsKey(msg.getId())) {
                                    uniqueMessages.put(msg.getId(), msg);
                                    fullHistory.add(msg);
                                }
                            }
                        }

                        if (isEnd) {
                            NotificationCenter.getInstance(currentAccount).removeObserver(this, id);
                            AndroidUtilities.runOnUIThread(() -> {
                                Collections.sort(fullHistory, (o1, o2) -> Integer.compare(o1.getId(), o2.getId()));
                                progressDialog.setMessage("Generating export for " + fullHistory.size() + " messages...");
                                performActualExport(context, fullHistory, title, progressDialog);
                            });
                        } else {
                            int lastId = loaded.get(loaded.size() - 1).getId();
                            AndroidUtilities.runOnUIThread(() -> {
                                progressDialog.setMessage("Loading full chat... (" + fullHistory.size() + " messages)");
                                MessagesController.getInstance(currentAccount).loadMessages(dialogId, 0L, false, 100, lastId, 0, false, 0, 0, 0, 0, 0, 0L, 0, 0, 0, 0, true, 0, false);
                            });
                        }
                    }
                }
            }
        };

        NotificationCenter.getInstance(currentAccount).addObserver(delegate, NotificationCenter.messagesDidLoad);
        MessagesController.getInstance(currentAccount).loadMessages(dialogId, 0L, false, 100, 0, 0, false, 0, 0, 0, 0, 0, 0L, 0, 0, 0, 0, true, 0, false);
    }

    private static void performActualExport(final Context context, final ArrayList<MessageObject> messages, final String title, final org.telegram.ui.ActionBar.AlertDialog progressDialog) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                File downloadsDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AlexgramExports");
                String folderName = "Export_" + title.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis();
                File exportDir = new File(downloadsDir, folderName);
                exportDir.mkdirs();

                File mediaDir = new File(exportDir, "media");
                mediaDir.mkdirs();

                StringBuilder html = new StringBuilder();
                html.append("<!DOCTYPE html>\n<html>\n<head>\n");
                html.append("<meta charset=\"UTF-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
                html.append("<title>Chat Export: ").append(title).append("</title>\n");
                html.append("<style>\n");
                html.append("body { font-family: 'Segoe UI', system-ui, -apple-system, sans-serif; background: #e7ebf0; margin: 0; padding: 20px; display: flex; justify-content: center; height: 100vh; box-sizing: border-box; }\n");
                html.append(".chat-container { width: 100%; max-width: 900px; background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0,0,0,0.15); overflow: hidden; display: flex; flex-direction: column; height: 100%; position: relative; }\n");
                html.append(".header { background: #517da2; color: #fff; padding: 15px 25px; display: flex; align-items: center; border-bottom: 1px solid rgba(0,0,0,0.1); flex-shrink: 0; z-index: 10; }\n");
                html.append(".header img { width: 50px; height: 50px; border-radius: 50%; margin-right: 18px; object-fit: cover; background: rgba(255,255,255,0.2); border: 2px solid rgba(255,255,255,0.5); }\n");
                html.append(".header-info { display: flex; flex-direction: column; }\n");
                html.append(".chat-title { font-size: 20px; font-weight: 600; text-shadow: 0 1px 2px rgba(0,0,0,0.2); }\n");
                html.append(".export-info { font-size: 11px; opacity: 0.8; margin-top: 2px; }\n");
                html.append(".messages { padding: 20px; overflow-y: auto; flex-grow: 1; background: #f0f2f5; display: flex; flex-direction: column; gap: 8px; background-image: url('data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI4MCIgaGVpZ2h0PSI4MCI+PGNpcmNsZSBjeD0iNDAiIGN5PSI0MCIgcj0iMiIgZmlsbD0icmdiYSgwLDAsMCwwLjA1KSIvPjwvc3ZnPg=='); }\n");
                html.append(".message { max-width: 75%; padding: 8px 12px; border-radius: 12px; position: relative; font-size: 15px; line-height: 1.5; color: #222; word-wrap: break-word; box-shadow: 0 1px 4px rgba(0,0,0,0.1); transition: transform 0.2s; }\n");
                html.append(".message:hover { transform: scale(1.005); }\n");
                html.append(".message.in { align-self: flex-start; background: #fff; border-bottom-left-radius: 4px; }\n");
                html.append(".message.out { align-self: flex-end; background: #effdde; border-bottom-right-radius: 4px; }\n");
                html.append(".sender { font-weight: 600; font-size: 13px; margin-bottom: 3px; color: #3390ec; display: block; }\n");
                html.append(".out .sender { color: #4fae4e; }\n");
                html.append(".text { white-space: pre-wrap; }\n");
                html.append(".time { font-size: 11px; color: #a0a0a0; margin-top: 4px; text-align: right; display: inline-block; width: 100%; border-top: 1px solid rgba(0,0,0,0.03); padding-top: 4px; }\n");
                html.append(".media { margin: 6px -4px -4px -4px; border-radius: 8px; overflow: hidden; max-width: 100%; border: 1px solid rgba(0,0,0,0.1); }\n");
                html.append(".media img, .media video { width: 100%; display: block; cursor: pointer; transition: opacity 0.2s; }\n");
                html.append(".media audio { width: 100%; margin-top: 8px; }\n");
                html.append(".reply { background: rgba(0,0,0,0.04); border-left: 3px solid #3390ec; padding: 6px 12px; margin-bottom: 8px; border-radius: 6px; cursor: pointer; transition: opacity 0.2s; }\n");
                html.append(".reply:hover { opacity: 0.8; }\n");
                html.append(".reply-sender { font-weight: bold; font-size: 12px; color: #3390ec; }\n");
                html.append(".reply-text { font-size: 12px; color: #666; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }\n");
                html.append(".file-box { display: flex; align-items: center; background: rgba(0,0,0,0.05); padding: 12px; border-radius: 10px; text-decoration: none; color: inherit; border: 1px solid rgba(0,0,0,0.1); }\n");
                html.append(".file-icon { width: 44px; height: 44px; background: #3390ec; color: #fff; border-radius: 12px; display: flex; align-items: center; justify-content: center; margin-right: 15px; font-weight: bold; text-transform: uppercase; font-size: 12px; }\n");
                html.append(".file-info { flex-grow: 1; overflow: hidden; }\n");
                html.append(".file-name { font-weight: 600; font-size: 14px; text-overflow: ellipsis; overflow: hidden; white-space: nowrap; color: #3390ec; }\n");
                html.append(".file-size { font-size: 12px; color: #888; margin-top: 2px; }\n");
                html.append(".date-divider { align-self: center; background: rgba(0,0,0,0.08); color: #555; padding: 4px 15px; border-radius: 20px; font-size: 13px; font-weight: 600; margin: 15px 0; text-transform: uppercase; z-index: 5; }\n");
                html.append(".modal { display: none; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.9); align-items: center; justify-content: center; backdrop-filter: blur(8px); animation: fadeIn 0.3s; }\n");
                html.append(".modal-content { max-width: 90%; max-height: 90%; display: flex; align-items: center; justify-content: center; position: relative; }\n");
                html.append(".modal-content img, .modal-content video { max-width: 100%; max-height: 100%; border-radius: 12px; box-shadow: 0 0 50px rgba(0,0,0,0.5); object-fit: contain; }\n");
                html.append(".modal-close { position: absolute; top: -50px; right: 0; color: #fff; font-size: 40px; cursor: pointer; transition: 0.3s; font-family: Arial; width: 40px; height: 40px; display: flex; align-items: center; justify-content: center; user-select: none; }\n");
                html.append(".modal-close:hover { transform: scale(1.1); color: #ccc; }\n");
                html.append("@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }\n");
                html.append("::-webkit-scrollbar { width: 8px; }\n");
                html.append("::-webkit-scrollbar-track { background: transparent; }\n");
                html.append("::-webkit-scrollbar-thumb { background: #bbb; border-radius: 10px; }\n");
                html.append("::-webkit-scrollbar-thumb:hover { background: #999; }\n");
                html.append("@media (prefers-color-scheme: dark) {\n");
                html.append("  body { background: #0e1621; }\n");
                html.append("  .chat-container { background: #17212b; color: #f5f5f5; box-shadow: 0 10px 40px rgba(0,0,0,0.5); }\n");
                html.append("  .header { background: #242f3d; }\n");
                html.append("  .messages { background: #0e1621; background-image: url('data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI4MCIgaGVpZ2h0PSI4MCI+PGNpcmNsZSBjeD0iNDAiIGN5PSI0MCIgcj0iMiIgZmlsbD0icmdiYSgyNTUsMjU1LDI1NSwwLjA1KSIvPjwvc3ZnPg=='); }\n");
                html.append("  .message.in { background: #182533; color: #fff; border-color: #242f3d; }\n");
                html.append("  .message.out { background: #2b5278; color: #fff; border-color: #2b5278; }\n");
                html.append("  .sender { color: #64b5f6; }\n");
                html.append("  .out .sender { color: #bbdefb; }\n");
                html.append("  .time { color: #8293a1; border-top-color: rgba(255,255,255,0.05); }\n");
                html.append("  .reply { background: rgba(255,255,255,0.08); border-left-color: #64b5f6; }\n");
                html.append("  .reply-sender { color: #64b5f6; }\n");
                html.append("  .reply-text { color: #aaa; }\n");
                html.append("  .file-box { background: rgba(255,255,255,0.05); border-color: rgba(255,255,255,0.1); }\n");
                html.append("  .file-name { color: #64b5f6; }\n");
                html.append("  .date-divider { background: rgba(255,255,255,0.1); color: #aaa; }\n");
                html.append("  ::-webkit-scrollbar-thumb { background: #444; }\n");
                html.append("}\n");
                html.append("</style>\n");
                html.append("</head>\n<body>\n");
                html.append("<div class=\"chat-container\">\n");
                html.append("<div class=\"header\">\n");

                // Export Chat Avatar
                long chatId = messages.get(0).getDialogId();
                String avatarFileName = null;
                if (chatId > 0) {
                    TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(chatId);
                    if (user != null && user.photo != null) {
                        File avatarFile = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(user.photo.photo_small, true);
                        if (avatarFile.exists()) {
                            avatarFileName = "avatar_" + user.id + ".jpg";
                            File photosDir = new File(mediaDir, "photos");
                            if (!photosDir.exists()) photosDir.mkdirs();
                            copyFile(avatarFile, new File(photosDir, avatarFileName));
                        }
                    }
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-chatId);
                    if (chat != null && chat.photo != null) {
                        File avatarFile = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(chat.photo.photo_small, true);
                        if (avatarFile.exists()) {
                            avatarFileName = "avatar_chat_" + chat.id + ".jpg";
                            File photosDir = new File(mediaDir, "photos");
                            if (!photosDir.exists()) photosDir.mkdirs();
                            copyFile(avatarFile, new File(photosDir, avatarFileName));
                        }
                    }
                }

                if (avatarFileName != null) {
                    html.append("<img src=\"media/photos/").append(avatarFileName).append("\">");
                }
                html.append("<div class=\"header-info\">");
                html.append("<div class=\"chat-title\">").append(title).append("</div>");
                html.append("<div class=\"export-info\">").append(messages.size()).append(" messages | Exported on ").append(new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(new Date())).append("</div>");
                html.append("</div>");
                html.append("</div>\n");
                html.append("<div class=\"messages\">\n");

                // Sort messages by date to ensure chronological order
                Collections.sort(messages, (m1, m2) -> {
                    if (m1.messageOwner.date != m2.messageOwner.date) {
                        return Integer.compare(m1.messageOwner.date, m2.messageOwner.date);
                    }
                    return Integer.compare(m1.getId(), m2.getId());
                });

                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
                String lastDate = "";

                HashMap<Integer, MessageObject> msgMap = new HashMap<>();
                for (MessageObject msg : messages) msgMap.put(msg.getId(), msg);

                for (MessageObject msg : messages) {
                    if (msg == null) continue;

                    // Date Divider
                    String currentDate = dateFormat.format(new Date(msg.messageOwner.date * 1000L));
                    if (!currentDate.equals(lastDate)) {
                        html.append("<div class=\"date-divider\">").append(currentDate).append("</div>\n");
                        lastDate = currentDate;
                    }

                    boolean isOut = msg.isOut();
                    html.append("<div class=\"message ").append(isOut ? "out" : "in").append("\">\n");

                    // Sender Name
                    String senderName = getSenderName(msg);
                    if (!isOut) {
                        html.append("<span class=\"sender\">").append(senderName).append("</span>\n");
                    }

                    // Reply
                    if (msg.isReply()) {
                        int replyId = msg.getReplyMsgId();
                        MessageObject replyMsg = msgMap.get(replyId);
                        if (replyMsg != null) {
                            html.append("<div class=\"reply\">\n");
                            html.append("<div class=\"reply-sender\">").append(getSenderName(replyMsg)).append("</div>\n");
                            html.append("<div class=\"reply-text\">").append(replyMsg.messageText).append("</div>\n");
                            html.append("</div>\n");
                        }
                    }

                    // Media
                    String mediaPath = MessageHelper.getPathToMessage(msg);
                    if (mediaPath != null) {
                        File f = new File(mediaPath);
                        if (f.exists()) {
                            String subDir = "files";
                            if (msg.isPhoto()) subDir = "photos";
                            else if (msg.isVideo() || msg.isRoundVideo()) subDir = "videos";
                            else if (msg.isVoice()) subDir = "voice";
                            else if (msg.isMusic()) subDir = "audio";
                            else if (msg.isSticker() || msg.isAnimatedSticker()) subDir = "stickers";

                            File targetSubDir = new File(mediaDir, subDir);
                            if (!targetSubDir.exists()) targetSubDir.mkdirs();

                            String fName = "file_" + msg.getId() + "_" + f.getName();
                            copyFile(f, new File(targetSubDir, fName));
                            String relativePath = "media/" + subDir + "/" + fName;

                            html.append("<div class=\"media\">");
                            boolean isVideo = msg.isVideo() || msg.isRoundVideo();
                            if (!isVideo && msg.getDocument() != null && msg.getDocument().file_name != null && msg.getDocument().file_name.toLowerCase().endsWith(".webm")) {
                                isVideo = true;
                            }
                            if (isVideo) {
                                html.append("<video src=\"").append(relativePath).append("\" onclick=\"openMedia('").append(relativePath).append("', 'video')\"></video>");
                            } else if (msg.isPhoto() || msg.isSticker() || msg.isAnimatedSticker()) {
                                html.append("<img src=\"").append(relativePath).append("\" onclick=\"openMedia('").append(relativePath).append("', 'img')\">");
                            } else if (msg.isVoice() || msg.isMusic()) {
                                html.append("<audio controls src=\"").append(relativePath).append("\"></audio>");
                            } else {
                                html.append("<a class=\"file-box\" href=\"").append(relativePath).append("\" download>");
                                html.append("<div class=\"file-icon\">Doc</div>");
                                html.append("<div class=\"file-info\">");
                                html.append("<div class=\"file-name\">").append(f.getName()).append("</div>");
                                html.append("<div class=\"file-size\">").append(AndroidUtilities.formatFileSize(f.length())).append("</div>");
                                html.append("</div></a>");
                            }
                            html.append("</div>");
                        }
                    }

                    // Text
                    if (!TextUtils.isEmpty(msg.messageText)) {
                        html.append("<div class=\"text\">").append(msg.messageText).append("</div>\n");
                    }

                    html.append("<div class=\"time\">").append(timeFormat.format(new Date(msg.messageOwner.date * 1000L))).append("</div>\n");
                    html.append("</div>\n");
                }

                html.append("</div>\n</div>\n");

                // Media Modal HTML
                html.append("<div id=\"media-modal\" class=\"modal\">\n");
                html.append("<div class=\"modal-content\">\n");
                html.append("<div class=\"modal-close\" onclick=\"closeMedia()\">&times;</div>\n");
                html.append("<img id=\"modal-img\" style=\"display:none\">\n");
                html.append("<video id=\"modal-video\" controls style=\"display:none\"></video>\n");
                html.append("</div>\n</div>\n");

                // Media Modal JS
                html.append("<script>\n");
                html.append("function openMedia(src, type) {\n");
                html.append("    const modal = document.getElementById('media-modal');\n");
                html.append("    const img = document.getElementById('modal-img');\n");
                html.append("    const video = document.getElementById('modal-video');\n");
                html.append("    img.style.display = 'none';\n");
                html.append("    video.style.display = 'none';\n");
                html.append("    if (type === 'img') {\n");
                html.append("        img.src = src;\n");
                html.append("        img.style.display = 'block';\n");
                html.append("    } else {\n");
                html.append("        video.src = src;\n");
                html.append("        video.style.display = 'block';\n");
                html.append("        video.play();\n");
                html.append("    }\n");
                html.append("    modal.style.display = 'flex';\n");
                html.append("}\n");
                html.append("function closeMedia() {\n");
                html.append("    const modal = document.getElementById('media-modal');\n");
                html.append("    const video = document.getElementById('modal-video');\n");
                html.append("    modal.style.display = 'none';\n");
                html.append("    video.pause();\n");
                html.append("}\n");
                html.append("window.onclick = function(event) { if (event.target == document.getElementById('media-modal')) closeMedia(); }\n");
                html.append("</script>\n");

                html.append("</body>\n</html>");

                File indexFile = new File(exportDir, "index.html");
                FileOutputStream fos = new FileOutputStream(indexFile);
                fos.write(html.toString().getBytes());
                fos.close();

                // Zip the entire directory
                File zipFile = new File(downloadsDir, folderName + ".zip");
                zipDirectory(exportDir, zipFile);
                
                // Cleanup folder after zipping
                deleteDirectory(exportDir);

                final File finalZip = zipFile;
                AndroidUtilities.runOnUIThread(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    Toast.makeText(context, "Exported Zip to Downloads", Toast.LENGTH_LONG).show();
                    
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("application/zip");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(context, context.getPackageName() + ".provider", finalZip));
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(Intent.createChooser(shareIntent, "Share Export"));
                });

            } catch (final Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    Toast.makeText(context, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private static String getSenderName(MessageObject msg) {
        String senderName = "Unknown";
        long senderId = msg.getSenderId();
        if (senderId > 0) {
            TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(senderId);
            if (user != null) senderName = org.telegram.messenger.UserObject.getUserName(user);
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-senderId);
            if (chat != null) senderName = chat.title;
        }
        return senderName;
    }

    private static void copyFile(File source, File dest) throws Exception {
        try (InputStream is = new FileInputStream(source); FileOutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024 * 64];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        }
    }

    private static void zipDirectory(File inputDir, File zipFile) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            zipFolder(inputDir, inputDir, zos);
        }
    }

    private static void zipFolder(File root, File folder, ZipOutputStream zos) throws Exception {
        File[] files = folder.listFiles();
        if (files == null) return;
        byte[] buffer = new byte[1024 * 64];
        for (File f : files) {
            if (f.isDirectory()) {
                zipFolder(root, f, zos);
            } else {
                String entryName = f.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
                ZipEntry ze = new ZipEntry(entryName);
                zos.putNextEntry(ze);
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f))) {
                    int length;
                    while ((length = bis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    public static void importChat(BaseFragment fragment, int reqCode) {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            fragment.startActivityForResult(Intent.createChooser(intent, "Import Chat JSON"), reqCode);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void handleImportResult(Context context, long dialogId, Uri uri) {
        if (uri == null) return;
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            is.close();

            JSONArray jsonArray = new JSONArray(sb.toString());
            int count = 0;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String text = obj.optString("message");
                if (text != null && !text.isEmpty()) {
                    SendMessagesHelper.getInstance(UserConfig.selectedAccount).sendMessage(text, dialogId, null, null, null, true, null, null, null, true, 0, 0, null, false);
                    count++;
                }
            }

            Toast.makeText(context, "Imported " + count + " messages.", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(context, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}

