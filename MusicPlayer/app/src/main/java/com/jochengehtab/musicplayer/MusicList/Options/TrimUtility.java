package com.jochengehtab.musicplayer.MusicList.Options;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.documentfile.provider.DocumentFile;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

public class TrimUtility {
    private final Context context;

    public TrimUtility(Context context) {
        this.context = context;
    }

    public DocumentFile validateBackupFolder(DocumentFile treeRoot) {
        final String BACKUPS_FOLDER_NAME = "Backups";
        DocumentFile backupsFolder = treeRoot.findFile(BACKUPS_FOLDER_NAME);
        if (backupsFolder == null) {
            // Didn’t exist yet, so create it
            backupsFolder = treeRoot.createDirectory(BACKUPS_FOLDER_NAME);
            if (backupsFolder == null) {
                throw new RuntimeException("Failed to create “Backups” folder under the chosen tree.");
            }
        } else if (!backupsFolder.isDirectory()) {
            throw new RuntimeException("A non‐folder named “Backups” already exists in that tree.");
        }
        return backupsFolder;
    }

    public void backUpFile(DocumentFile backupsFolder, Uri originalUri, String mimeType, String backupName) {
        DocumentFile existingBackup = backupsFolder.createFile(Objects.requireNonNull(mimeType), backupName);
        if (existingBackup == null) {
            throw new RuntimeException("Failed to create backup file \"" + backupName + "\".");
        }

        try (
                ParcelFileDescriptor pfdIn  = context.getContentResolver()
                        .openFileDescriptor(originalUri, "r");
                ParcelFileDescriptor pfdOut = context.getContentResolver()
                        .openFileDescriptor(existingBackup.getUri(), "w");
                FileInputStream inStream   = new FileInputStream(Objects.requireNonNull(pfdIn).getFileDescriptor());
                FileOutputStream outStream  = new FileOutputStream(Objects.requireNonNull(pfdOut).getFileDescriptor())
        ) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inStream.read(buffer)) > 0) {
                outStream.write(buffer, 0, bytesRead);
            }
            outStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String generateBackupName(String fullName) {
        // Get the index of the dot of the file extension
        int dotIndex = fullName.lastIndexOf('.');
        String baseName = (dotIndex >= 0 ? fullName.substring(0, dotIndex) : fullName);
        String extension = (dotIndex >= 0 ? fullName.substring(dotIndex) : "");
        // Sanitize the baseName to remove illegal characters
        String sanitizedBaseName = baseName.replaceAll("[\\\\/:*?\"<>|]", "_");

        return sanitizedBaseName + "_backup" + extension;
    }
}
