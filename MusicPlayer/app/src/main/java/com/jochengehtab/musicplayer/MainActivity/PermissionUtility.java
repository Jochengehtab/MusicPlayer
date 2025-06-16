package com.jochengehtab.musicplayer.MainActivity;

import android.content.ContentResolver;
import android.content.UriPermission;
import android.net.Uri;

import java.util.List;

public class PermissionUtility {
    public static boolean hasPersistedPermissions(Uri musicDirectoryUri, ContentResolver contentResolver) {
        if (musicDirectoryUri == null) {
            return false;
        }

        List<UriPermission> persistedPermissions = contentResolver.getPersistedUriPermissions();
        for (UriPermission permission : persistedPermissions) {
            if (permission.getUri().equals(musicDirectoryUri) && permission.isWritePermission()) {
                // We found our URI and it has write permission
                return true;
            }
        }

        // If we get here, we either didn't find our URI or it didn't have write permission
        return false;
    }
}
