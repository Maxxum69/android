package mega.privacy.android.app.utils;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.IOException;

import static mega.privacy.android.app.utils.FileUtils.*;
import static mega.privacy.android.app.utils.Util.getSizeString;

public final class CacheFolderManager {

    public static final String THUMBNAIL_FOLDER = "thumbnailsMEGA";

    public static final String PREVIEW_FOLDER = "previewsMEGA";

    public static final String AVATAR_FOLDER = "avatarsMEGA";

    public static final String QR_FOLDER = "qrMEGA";

    public static final String TEMPORAL_FOLDER = "tempMEGA";

    public static final String CHAT_TEMPORAL_FOLDER = "chatTempMEGA";

    public static final String oldTemporalPicDIR = "MEGA/MEGA AppTemp";

    public static final String oldProfilePicDIR = "MEGA/MEGA Profile Images";

    public static final String oldAdvancesDevicesDIR = "MEGA/MEGA Temp";

    public static final String oldChatTempDIR = "MEGA/MEGA Temp/Chat";

    public static File getCacheFolder(Context context, String folderName) {
        log("create cache folder: " + folderName);
        File cacheFolder = new File(context.getCacheDir(), folderName);
        if (cacheFolder == null) return null;

        if (cacheFolder.exists()) {
            return cacheFolder;
        } else {
            if (cacheFolder.mkdir()) {
                return cacheFolder;
            } else {
                return null;
            }
        }
    }

    public static void createCacheFolders(Context context) {
        createCacheFolder(context, THUMBNAIL_FOLDER);
        createCacheFolder(context, PREVIEW_FOLDER);
        createCacheFolder(context, AVATAR_FOLDER);
        createCacheFolder(context, QR_FOLDER);
        removeOldTempFolders(context);
    }

    public static void clearPublicCache(final Context context) {
        new Thread() {
            @Override
            public void run() {
                File dir = context.getExternalCacheDir();
                if (dir != null) {
                    cleanDir(dir);
                }
            }
        }.start();
    }

    private static void createCacheFolder(Context context, String name) {
        File file = getCacheFolder(context, name);
        if (isFileAvailable(file)) {
            log(file.getName() + " folder created: " + file.getAbsolutePath());
        } else {
            log("create file failed");
        }
    }

    public static File buildQrFile(Context context, String fileName) {
        return getCacheFile(context, QR_FOLDER, fileName);
    }

    public static File buildPreviewFile(Context context, String fileName) {
        return getCacheFile(context, PREVIEW_FOLDER, fileName);
    }

    public static File buildAvatarFile(Context context, String fileName) {
        return getCacheFile(context, AVATAR_FOLDER, fileName);
    }

    public static File buildTempFile(Context context, String fileName) {
        return getCacheFile(context, TEMPORAL_FOLDER, fileName);
    }

    public static File buildChatTempFile(Context context, String fileName) {
        return getCacheFile(context, CHAT_TEMPORAL_FOLDER, fileName);
    }

    public static File getCacheFile(Context context, String folderName, String fileName) {
        File parent = getCacheFolder(context, folderName);
        if (!isFileAvailable(parent)) return null;

        return new File(parent, fileName);
    }

    public static String getCacheSize(Context context){
        log("getCacheSize");
        File cacheIntDir = context.getCacheDir();
        File cacheExtDir = context.getExternalCacheDir();

        if(cacheIntDir!=null){
            log("Path to check internal: "+cacheIntDir.getAbsolutePath());
        }
        long size = getDirSize(cacheIntDir)+getDirSize(cacheExtDir);

        return getSizeString(size);
    }

    public static void clearCache(Context context){
        log("clearCache");
        File cacheIntDir = context.getCacheDir();
        File cacheExtDir = context.getExternalCacheDir();

        cleanDir(cacheIntDir);
        cleanDir(cacheExtDir);
    }

    public static void deleteCacheFolderIfEmpty (Context context, String folderName) {
        File folder = getCacheFolder(context, folderName);
        if (isFileAvailable(folder) && folder.list().length <= 0) {
            folder.delete();
        }
    }

    public static void removeOldTempFolders(final Context context) {
        new Thread() {
            @Override
            public void run() {
                removeOldTempFolder(context, oldTemporalPicDIR);
                removeOldTempFolder(context, oldProfilePicDIR);
                removeOldTempFolder(context, oldAdvancesDevicesDIR);
                removeOldTempFolder(context, oldChatTempDIR);
            }
        }.start();
    }

    private static void removeOldTempFolder(Context context, String folderName) {
        File oldTempFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + folderName);
        if (!isFileAvailable(oldTempFolder)) return;

        try {
            deleteFolderAndSubfolders(context, oldTempFolder);
        } catch (IOException e) {
            log("Exception deleting" + oldTempFolder.getName() + "directory");
            e.printStackTrace();
        }
    }

    public static void log(String message) {
        Util.log("CacheFolderManager", message);
    }
}
