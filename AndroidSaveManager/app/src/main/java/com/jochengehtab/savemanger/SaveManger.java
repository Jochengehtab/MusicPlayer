package com.jochengehtab.savemanger;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SaveManger {

    private Context context;
    private File file;

    public SaveManger(Context context){
        this.context = context;
    }

    public void load(String path){
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Path check = Paths.get(path);
            if (!Files.isDirectory(check)){
                throw new NullPointerException("Path is null");
            }
        }
        this.file = new File(path);
    }

    public void set(Object value) {

        if (file == null){
            throw new NullPointerException("File is null");
        }

        try {
            FileInputStream fileInputStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
