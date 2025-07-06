package com.jochengehtab.musicplayer.Utility;

import android.net.Uri;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

/**
 * A Gson TypeAdapter for Android's Uri class.
 * This tells Gson how to serialize a Uri to a string and deserialize a string back to a Uri.
 */
public class UriTypeAdapter extends TypeAdapter<Uri> {

    @Override
    public void write(JsonWriter out, Uri value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            // Write the Uri as a plain string
            out.value(value.toString());
        }
    }

    @Override
    public Uri read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        // Read the string from JSON and parse it back into a Uri
        return Uri.parse(in.nextString());
    }
}