package com.jochengehtab.musicplayer.MusicList.Options;

import androidx.documentfile.provider.DocumentFile;

public class TrimUtility {
    private final String BACKUPS_FOLDER_NAME = "Backups";

    public DocumentFile validateBackupFolder(DocumentFile treeRoot) {
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
}
