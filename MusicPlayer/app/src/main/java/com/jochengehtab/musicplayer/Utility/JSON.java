package com.jochengehtab.musicplayer.Utility;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JSON manager for reading and writing named values into a JSON file using Gson and SAF URIs.
 * Supports primitive values, objects, and arrays/lists.
 * The SAF tree URI is stored in SharedPreferences, so no file-system paths are used.
 */
public class JSON {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Context context;
    private final DocumentFile configFile;

    /**
     * Constructs a JSON manager using a persisted SAF tree URI in SharedPreferences.
     *
     * @param context    Android context
     * @param prefsName  Name of SharedPreferences where tree URI is stored
     * @param keyTreeUri Key under which the tree URI is saved
     * @param fileName   Name of the JSON file to use (e.g. "config.json")
     * @throws RuntimeException if the URI is missing or invalid
     */
    public JSON(Context context, String prefsName, String keyTreeUri, String fileName) {
        this.context = context.getApplicationContext();
        String treeUriString = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .getString(keyTreeUri, null);
        if (treeUriString == null) {
            throw new RuntimeException("No tree URI found in SharedPreferences under key '" + keyTreeUri + "'");
        }
        Uri treeUri = Uri.parse(treeUriString);
        DocumentFile treeRoot = DocumentFile.fromTreeUri(this.context, treeUri);
        if (treeRoot == null || !treeRoot.isDirectory()) {
            throw new RuntimeException("Invalid or inaccessible SAF tree URI: " + treeUriString);
        }
        // Find or create the JSON file in the tree root
        DocumentFile file = treeRoot.findFile(fileName);
        if (file == null) {
            file = treeRoot.createFile("application/json", fileName);
            if (file == null) {
                throw new RuntimeException("Unable to create file '" + fileName + "' in tree.");
            }
            // Initialize empty JSON object
            try (Writer w = new OutputStreamWriter(
                    context.getContentResolver().openOutputStream(file.getUri()), StandardCharsets.UTF_8)) {
                w.write("{}");
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize " + fileName, e);
            }
        }
        this.configFile = file;
    }

    /**
     * Constructs a JSON manager from a direct DocumentFile reference.
     * This is ideal for files in subdirectories, like a playlist.
     *
     * @param context    Android context
     * @param configFile The DocumentFile representing the JSON file.
     * @throws RuntimeException if the configFile is invalid or cannot be initialized.
     */
    public JSON(Context context, DocumentFile configFile) {
        this.context = context.getApplicationContext();
        this.configFile = configFile;

        // Ensure the file is not empty to prevent parsing errors
        try {
            if (configFile.length() == 0) {
                // Initialize with an empty JSON object
                try (Writer w = new OutputStreamWriter(
                        context.getContentResolver().openOutputStream(configFile.getUri()), StandardCharsets.UTF_8)) {
                    w.write("{}");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize config file: " + configFile.getName(), e);
        }
    }

    /**
     * Writes a named value into the JSON file. Previous entries are preserved.
     * Supports primitives, objects, lists, and arrays.
     *
     * @param key   the variable name
     * @param value the value to store (any serializable object or collection)
     */
    public void write(String key, Object value) {
        try {
            JsonObject root;
            // Read existing JSON
            try (Reader r = new InputStreamReader(
                    context.getContentResolver().openInputStream(configFile.getUri()), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            // Convert and insert
            JsonElement element = gson.toJsonTree(value);
            root.add(key, element);
            // Overwrite file
            try (Writer w = new OutputStreamWriter(
                    context.getContentResolver().openOutputStream(configFile.getUri(), "wt"), StandardCharsets.UTF_8)) {
                gson.toJson(root, w);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write key '" + key + "' to config file", e);
        }
    }

    /**
     * Reads a named value from the JSON file and deserializes it into the specified class.
     *
     * @param key   the variable name
     * @param clazz the class to deserialize into
     * @param <T>   the type parameter
     * @return the deserialized value, or null if the key does not exist
     */
    public <T> T read(String key, Class<T> clazz) {
        try (Reader r = new InputStreamReader(
                context.getContentResolver().openInputStream(configFile.getUri()), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            JsonElement element = root.get(key);
            if (element == null || element.isJsonNull()) return null;
            return gson.fromJson(element, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read key '" + key + "' from config file", e);
        }
    }

    /**
     * Reads a named array or list from the JSON file into a List of the given element type.
     *
     * @param key          the variable name
     * @param elementClass the class of each element in the list
     * @param <E>          element type
     * @return a List of elements, or null if the key does not exist
     */
    public <E> List<E> readList(String key, Class<E> elementClass) {
        try (Reader r = new InputStreamReader(
                context.getContentResolver().openInputStream(configFile.getUri()), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            JsonElement element = root.get(key);
            if (element == null || element.isJsonNull()) return null;
            Type listType = TypeToken.getParameterized(List.class, elementClass).getType();
            return gson.fromJson(element, listType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read list '" + key + "' from config file", e);
        }
    }

    /**
     * Reads a named array from the JSON file into an array of the given type.
     *
     * @param key        the variable name
     * @param arrayClass the array class (e.g. String[].class)
     * @param <T>        array element type
     * @return an array of T, or null if the key does not exist
     */
    public <T> T[] readArray(String key, Class<T[]> arrayClass) {
        try (Reader r = new InputStreamReader(
                context.getContentResolver().openInputStream(configFile.getUri()), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            JsonElement element = root.get(key);
            if (element == null || element.isJsonNull()) return null;
            return gson.fromJson(element, arrayClass);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read array '" + key + "' from config file", e);
        }
    }
}
