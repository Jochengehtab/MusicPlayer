package com.jochengehtab.musicplayer.Utility;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A simplified and robust JSON manager using a consistent read-modify-write pattern.
 * This ensures that custom TypeAdapters (like for Uri) are always used correctly.
 */
public class JSON {
    private final Gson gson;
    private final Context context;
    private final DocumentFile configFile;

    /**
     * Constructs a JSON manager using a persisted SAF tree URI in SharedPreferences.
     */
    public JSON(Context context, String prefsName, String keyTreeUri, String fileName) {
        this.context = context.getApplicationContext();
        String treeUriString = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .getString(keyTreeUri, null);
        if (treeUriString == null) {
            throw new RuntimeException("No tree URI in SharedPreferences for key '" + keyTreeUri + "'");
        }
        Uri treeUri = Uri.parse(treeUriString);
        DocumentFile treeRoot = DocumentFile.fromTreeUri(this.context, treeUri);
        if (treeRoot == null || !treeRoot.isDirectory()) {
            throw new RuntimeException("Invalid SAF tree URI: " + treeUriString);
        }

        DocumentFile file = treeRoot.findFile(fileName);
        if (file == null) {
            file = treeRoot.createFile("application/json", fileName);
            if (file == null) {
                throw new RuntimeException("Unable to create file '" + fileName + "'");
            }
        }
        this.configFile = file;

        // Initialize Gson with our custom adapter
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Uri.class, new UriTypeAdapter())
                .setPrettyPrinting() // Makes the JSON file human-readable
                .create();
    }

    /**
     * Constructs a JSON manager from a direct DocumentFile reference.
     */
    public JSON(Context context, DocumentFile configFile) {
        this.context = context.getApplicationContext();
        this.configFile = configFile;

        // Initialize Gson with our custom adapter
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Uri.class, new UriTypeAdapter())
                .setPrettyPrinting() // Makes the JSON file human-readable
                .create();
    }

    /**
     * Reads the entire JSON file into a Map.
     *
     * @return A map representing the JSON content. Returns an empty map if the file is empty or new.
     */
    private Map<String, JsonElement> readAsMap() throws IOException {
        // If file is empty or doesn't exist, return a new map
        if (configFile.length() == 0) {
            return new LinkedHashMap<>();
        }

        try (Reader reader = new InputStreamReader(
                context.getContentResolver().openInputStream(configFile.getUri()), StandardCharsets.UTF_8)) {

            Type type = new TypeToken<LinkedHashMap<String, JsonElement>>() {
            }.getType();
            Map<String, JsonElement> map = gson.fromJson(reader, type);
            return (map != null) ? map : new LinkedHashMap<>();
        }
    }

    /**
     * Writes a key-value pair to the JSON file, preserving other content.
     */
    public void write(String key, Object value) {
        try {
            Map<String, JsonElement> root = readAsMap();
            // Convert the new value to a JsonElement using our custom Gson instance
            JsonElement element = gson.toJsonTree(value);
            root.put(key, element);

            // Write the entire updated map back to the file
            try (Writer writer = new OutputStreamWriter(
                    context.getContentResolver().openOutputStream(configFile.getUri(), "w"), StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to config file: " + configFile.getName(), e);
        }
    }

    /**
     * Reads a value from the JSON file.
     */
    public <T> T read(String key, Class<T> clazz) {
        try {
            Map<String, JsonElement> root = readAsMap();
            JsonElement element = root.get(key);
            if (element == null || element.isJsonNull()) {
                return null;
            }
            // Use our custom Gson instance to deserialize the element
            return gson.fromJson(element, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read from config file: " + configFile.getName(), e);
        }
    }

    /**
     * Reads a list from the JSON file.
     */
    public <E> List<E> readList(String key, Class<E> elementClass) {
        try {
            Map<String, JsonElement> root = readAsMap();
            JsonElement element = root.get(key);
            if (element == null || element.isJsonNull()) {
                return null;
            }
            // Define the type for a List of our element class
            Type listType = TypeToken.getParameterized(List.class, elementClass).getType();
            // Use our custom Gson instance to deserialize
            return gson.fromJson(element, listType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read list from config file: " + configFile.getName(), e);
        }
    }

    /**
     * Reads an array from the JSON file.
     */
    public <T> T[] readArray(String key, Class<T[]> arrayClass) {
        try {
            Map<String, JsonElement> root = readAsMap();
            JsonElement element = root.get(key);
            if (element == null || element.isJsonNull()) {
                return null;
            }
            return gson.fromJson(element, arrayClass);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read array from config file: " + configFile.getName(), e);
        }
    }

    /**
     * Removes a key-value pair from the JSON file.
     */
    public void remove(String key) {
        try {
            Map<String, JsonElement> root = readAsMap();
            if (root.containsKey(key)) {
                root.remove(key);
                // Write the entire updated map back to the file
                try (Writer writer = new OutputStreamWriter(
                        context.getContentResolver().openOutputStream(configFile.getUri(), "w"), StandardCharsets.UTF_8)) {
                    gson.toJson(root, writer);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove key from config file: " + configFile.getName(), e);
        }
    }

    /**
     * Gets all top-level keys from the JSON file.
     * @return A Set of all keys.
     */
    public Set<String> getKeys() {
        try {
            return readAsMap().keySet();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read keys from config file: " + configFile.getName(), e);
        }
    }
}
