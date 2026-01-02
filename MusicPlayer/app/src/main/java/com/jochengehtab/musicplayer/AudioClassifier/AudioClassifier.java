package com.jochengehtab.musicplayer.AudioClassifier;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import org.pytorch.executorch.Module;
import org.pytorch.executorch.Tensor;
import org.pytorch.executorch.EValue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AudioClassifier {

    public interface AnalysisProgressListener {
        void onProgress(int percentage, String message);
    }

    private static final String TAG = "AudioClassifier";

    // Models
    public static final String YAMNET_MODEL = "yamnet_with_embeddings.tflite";
    public static final String CLASSIFIER_MODEL = "applause_silence_classifier.pte"; // CHANGED to .pte
    public static final String LABELS_FILE = "labels.txt";

    // Audio Constants
    public static final int SAMPLE_RATE = 16000;
    public static final float WINDOW_SEC = 1.0f;
    public static final float HOP_SEC = 0.5f;

    public static final int WINDOW_SAMPLES = (int) (SAMPLE_RATE * WINDOW_SEC);
    public static final int HOP_SAMPLES = (int) (SAMPLE_RATE * HOP_SEC);

    // --- Member Variables ---
    private final Context context;
    private final List<String> labels;

    // ExecuTorch Module
    private final Module classifier;

    // TFLite Interpreter
    private final Interpreter yamnet;

    // Buffers
    private final float[][] outputEmbeddings;
    private final Map<Integer, Object> yamnetOutputs;

    public AudioClassifier(Context context) {
        this.context = context;
        this.labels = loadLabels();

        try {
            // 1. Load ExecuTorch Model (Classifier)
            String classifierPath = assetFilePath(CLASSIFIER_MODEL);
            this.classifier = Module.load(classifierPath);

            // 2. Load TFLite Model (YAMNet)
            String yamnetPath = assetFilePath(YAMNET_MODEL);
            Interpreter.Options options = new Interpreter.Options();
            this.yamnet = new Interpreter(new File(yamnetPath), options);

            // Resize Input: 16000 samples (1.0 second)
            yamnet.resizeInput(0, new int[]{WINDOW_SAMPLES});
            yamnet.allocateTensors();

            // Reusable buffers to avoid GC thrashing
            float[][] outputScores = new float[2][521];
            this.outputEmbeddings = new float[2][1024];

            this.yamnetOutputs = new HashMap<>();
            this.yamnetOutputs.put(0, outputEmbeddings); // Index 0 is Embeddings
            this.yamnetOutputs.put(1, outputScores);     // Index 1 is Scores
        } catch (Exception e) {
            throw new RuntimeException("Error initializing models", e);
        }
    }

    public List<Event> analyzeAudio(Uri audioUri, AnalysisProgressListener listener) throws IOException {

        // Notify decoding start
        if (listener != null) listener.onProgress(0, "Decoding audio...");

        float[] waveform = decodeAudioFile(audioUri, listener);
        if (waveform.length == 0) return Collections.emptyList();

        ArrayList<Prediction> predictions = new ArrayList<>();

        int totalSamples = waveform.length - WINDOW_SAMPLES;

        // Loop through audio with overlap
        for (int startSample = 0; startSample <= waveform.length - WINDOW_SAMPLES; startSample += HOP_SAMPLES) {

            if (listener != null) {
                // Calculate percentage
                int percent = (int) (((float) startSample / totalSamples) * 100);
                listener.onProgress(percent, "Analyzing...");
            }

            int endSample = startSample + WINDOW_SAMPLES;

            // Extract the chunk
            float[] chunk = Arrays.copyOfRange(waveform, startSample, endSample);

            // 1. Get Features (TFLite)
            float[] embeddings = getYamnetEmbeddings(chunk);
            if (embeddings.length == 0) continue;

            // 2. Get Prediction (ExecuTorch)
            String label = getPrediction(embeddings);

            float currentTime = (float) startSample / SAMPLE_RATE;
            predictions.add(new Prediction(currentTime, label));
        }


        // Notify finishing
        if (listener != null) listener.onProgress(100, "Finalizing...");

        return consolidatePredictions(predictions);
    }

    /**
     * Runs YAMNet (TFLite) to get audio embeddings and calculates their mean.
     */
    private float[] getYamnetEmbeddings(float[] chunk) {
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(chunk.length * 4);
        inputBuffer.order(ByteOrder.nativeOrder());
        inputBuffer.asFloatBuffer().put(chunk);

        try {
            // Run Inference
            yamnet.runForMultipleInputsOutputs(new Object[]{inputBuffer}, yamnetOutputs);
        } catch (Exception e) {
            Log.e(TAG, "YAMNet inference failed", e);
            return new float[0];
        }

        // --- Process the embeddings output ---
        int embeddingDim = 1024;
        float[] meanEmbeddings = new float[embeddingDim];
        int rows = outputEmbeddings.length; // This is 2

        // Sum up the embeddings
        for (float[] outputEmbedding : outputEmbeddings) {
            for (int j = 0; j < embeddingDim; j++) {
                meanEmbeddings[j] += outputEmbedding[j];
            }
        }

        // Calculate Mean (Divide by 2)
        for (int i = 0; i < embeddingDim; i++) {
            meanEmbeddings[i] /= rows;
        }

        return meanEmbeddings;
    }

    /**
     * Runs ExecuTorch model on the embeddings.
     */
    private String getPrediction(float[] embeddings) {
        try {
            // 1. Prepare Tensor [1, 1024]
            long[] shape = {1, embeddings.length};
            Tensor inputTensor = Tensor.fromBlob(embeddings, shape);

            // 2. Wrap in EValue
            EValue inputEValue = EValue.from(inputTensor);

            // 3. Run Inference
            // forward() returns an array of EValues
            EValue[] outputs = classifier.forward(inputEValue);

            // 4. Unwrap Output
            // We expect the first output to be our logits/scores
            Tensor outputTensor = outputs[0].toTensor();
            float[] scores = outputTensor.getDataAsFloatArray();

            // 5. Find Max Score (ArgMax)
            int maxIndex = -1;
            float maxScore = -Float.MAX_VALUE;
            for (int i = 0; i < scores.length; i++) {
                if (scores[i] > maxScore) {
                    maxScore = scores[i];
                    maxIndex = i;
                }
            }

            return (maxIndex >= 0 && maxIndex < labels.size()) ? labels.get(maxIndex) : "Unknown";

        } catch (Exception e) {
            Log.e(TAG, "ExecuTorch inference failed", e);
            return "Error";
        }
    }

    private List<Event> consolidatePredictions(List<Prediction> predictions) {
        if (predictions.isEmpty()) return Collections.emptyList();
        ArrayList<Event> events = new ArrayList<>();
        Prediction first = predictions.get(0);
        Event currentEvent = new Event(first.label, first.time, first.time + HOP_SEC);

        for (int i = 1; i < predictions.size(); i++) {
            Prediction p = predictions.get(i);
            // If label matches and times are contiguous (within a small margin), extend
            if (p.label.equals(currentEvent.label)) {
                currentEvent.end = p.time + HOP_SEC;
            } else {
                events.add(currentEvent);
                currentEvent = new Event(p.label, p.time, p.time + HOP_SEC);
            }
        }
        events.add(currentEvent);
        return events;
    }

    private float[] decodeAudioFile(Uri audioUri, AnalysisProgressListener listener) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        ParcelFileDescriptor pfd = null;

        try {
            if (audioUri.getScheme() == null) {
                File file = new File(Objects.requireNonNull(audioUri.getPath()));
                audioUri = Uri.fromFile(file);
            }
            pfd = context.getContentResolver().openFileDescriptor(audioUri, "r");
            if (pfd == null) throw new IOException("Cannot open URI: " + audioUri);

            extractor.setDataSource(pfd.getFileDescriptor());
        } catch (Exception e) {
            Log.e(TAG, "Failed to set data source", e);
            if (pfd != null) pfd.close();
            return new float[0];
        }

        int trackIndex = -1;
        String mime = null;
        MediaFormat format = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            format = extractor.getTrackFormat(i);
            mime = format.getString(MediaFormat.KEY_MIME);
            if (Objects.requireNonNull(mime).startsWith("audio/")) {
                trackIndex = i;
                break;
            }
        }

        if (trackIndex == -1) {
            extractor.release();
            pfd.close();
            return new float[0];
        }

        extractor.selectTrack(trackIndex);
        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        codec.start();

        int inputSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ?
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;

        // Total Duration for progress calculation
        long durationUs = format.containsKey(MediaFormat.KEY_DURATION) ? format.getLong(MediaFormat.KEY_DURATION) : 0;

        int estimatedSamples = (int) ((durationUs / 1000000.0) * inputSampleRate * channelCount);
        if (estimatedSamples <= 0) estimatedSamples = 1024 * 1024;

        short[] rawData = new short[estimatedSamples];
        int sampleIndex = 0;

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputDone = false;
        boolean outputDone = false;

        // Progress tracking variable
        int lastReportedPercent = -1;

        while (!outputDone) {
            if (!inputDone) {
                int inputBufferIndex = codec.dequeueInputBuffer(5000);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferIndex);
                    assert inputBuffer != null;
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        long sampleTime = extractor.getSampleTime();
                        if (listener != null && durationUs > 0 && sampleTime > 0) {
                            int percent = (int) ((sampleTime * 100) / durationUs);
                            // Only update if percentage changed (avoids UI flooding)
                            if (percent > lastReportedPercent) {
                                listener.onProgress(percent, "Decoding audio...");
                                lastReportedPercent = percent;
                            }
                        }

                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
            }

            int outputBufferIndex = codec.dequeueOutputBuffer(info, 5000);
            if (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);
                if (outputBuffer != null) {
                    outputBuffer.position(info.offset);
                    outputBuffer.limit(info.offset + info.size);
                    outputBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    ShortBuffer sb = outputBuffer.asShortBuffer();
                    int remaining = sb.remaining();

                    if (sampleIndex + remaining > rawData.length) {
                        int newSize = Math.max(rawData.length + (rawData.length / 2), sampleIndex + remaining);
                        rawData = Arrays.copyOf(rawData, newSize);
                    }

                    sb.get(rawData, sampleIndex, remaining);
                    sampleIndex += remaining;
                }
                codec.releaseOutputBuffer(outputBufferIndex, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputDone = true;
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = codec.getOutputFormat();
                inputSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            }
        }

        codec.stop();
        codec.release();
        extractor.release();
        pfd.close();

        // Pass listener to resampling as well, as this can take time for large files
        return convertAndResample(rawData, sampleIndex, inputSampleRate, channelCount, listener);
    }

    /**
     * Converts short[] -> float[], mixes down stereo to mono, and resamples.
     */
    private float[] convertAndResample(short[] inputShorts, int length, int inputRate, int channels, AnalysisProgressListener listener) {
        if (listener != null) listener.onProgress(0, "Resampling...");

        // Mono Mixdown (if needed)
        int monoLength = (channels == 2) ? length / 2 : length;
        double ratio = (double) inputRate / AudioClassifier.SAMPLE_RATE;
        int targetLength = (int) (monoLength / ratio);
        float[] result = new float[targetLength];

        int lastReportedPercent = -1;

        for (int i = 0; i < targetLength; i++) {

            if (listener != null) {
                int percent = (int) (((float) i / targetLength) * 100);
                if (percent > lastReportedPercent) {
                    listener.onProgress(percent, "Resampling...");
                    lastReportedPercent = percent;
                }
            }

            double inputIndex = i * ratio;
            int idx1 = (int) inputIndex;
            int idx2 = idx1 + 1;
            double frac = inputIndex - idx1;

            float val1, val2;

            if (channels == 2) {
                // Read Stereo Frame 1
                int p1 = idx1 * 2;
                if (p1 + 1 < length) {
                    val1 = ((inputShorts[p1] + inputShorts[p1 + 1]) / 2.0f) / 32768.0f;
                } else {
                    val1 = 0;
                }

                // Read Stereo Frame 2 (for interpolation)
                int p2 = idx2 * 2;
                if (p2 + 1 < length) {
                    val2 = ((inputShorts[p2] + inputShorts[p2 + 1]) / 2.0f) / 32768.0f;
                } else {
                    val2 = val1; // End of stream
                }

            } else {
                // Read Mono Frame 1
                if (idx1 < length) {
                    val1 = inputShorts[idx1] / 32768.0f;
                } else {
                    val1 = 0;
                }

                // Read Mono Frame 2
                if (idx2 < length) {
                    val2 = inputShorts[idx2] / 32768.0f;
                } else {
                    val2 = val1;
                }
            }

            // Linear Interpolation
            result[i] = (float) (val1 * (1.0 - frac) + val2 * frac);
        }

        return result;
    }

    /**
     * Extracts a 1024-dimensional feature embedding from the given audio URI.
     * <p>
     * Optimization: Probes the file at 5 distinct intervals rather than decoding fully.
     *
     * @return The averaged embedding vector, or an empty array on failure.
     */
    public float[] getStyleEmbedding(Uri audioUri) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        ParcelFileDescriptor pfd = null;

        try {
            // 1. Setup Data Source
            if (audioUri.getScheme() == null) {
                File file = new File(Objects.requireNonNull(audioUri.getPath()));
                audioUri = Uri.fromFile(file);
            }
            pfd = context.getContentResolver().openFileDescriptor(audioUri, "r");
            if (pfd == null) return new float[0];
            extractor.setDataSource(pfd.getFileDescriptor());

            // 2. Select Track & Configure Codec
            int trackIndex = selectAudioTrack(extractor);
            if (trackIndex < 0) return new float[0];
            extractor.selectTrack(trackIndex);

            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            long durationUs = format.containsKey(MediaFormat.KEY_DURATION) ? format.getLong(MediaFormat.KEY_DURATION) : 0;

            assert mime != null;
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            // 3. Define Probes (Where to look)
            // If song is short (< 10s), just read start. Else probe 5 spots.
            long[] probePoints;
            if (durationUs < 10_000_000) {
                probePoints = new long[]{0};
            } else {
                probePoints = new long[]{
                        (long) (durationUs * 0.15),
                        (long) (durationUs * 0.30),
                        (long) (durationUs * 0.50),
                        (long) (durationUs * 0.70),
                        (long) (durationUs * 0.85)
                };
            }

            float[] sumEmbeddings = new float[1024];
            int validProbes = 0;

            // 4. Probe Loop
            for (long seekTime : probePoints) {
                // Seek to the point
                extractor.seekTo(seekTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                codec.flush(); // Essential after seek

                // Decode ~1.5 seconds of audio from this point
                float[] snippet = decodeSnippet(extractor, codec, format);

                // If we got enough data, analyze it
                if (snippet.length >= WINDOW_SAMPLES) {
                    // Take exact window size
                    float[] window = Arrays.copyOf(snippet, WINDOW_SAMPLES);
                    float[] features = getYamnetEmbeddings(window);

                    // Accumulate
                    for (int i = 0; i < 1024; i++) {
                        sumEmbeddings[i] += features[i];
                    }
                    validProbes++;
                }
            }

            if (validProbes == 0) return new float[0];

            // 5. Average
            float[] avgEmbedding = new float[1024];
            for (int i = 0; i < 1024; i++) {
                avgEmbedding[i] = sumEmbeddings[i] / validProbes;
            }

            return avgEmbedding;

        } catch (Exception e) {
            Log.e(TAG, "Fast Scan failed", e);
            return new float[0];
        } finally {
            if (codec != null) {
                try { codec.stop(); codec.release(); } catch (Exception ignored) {}
            }
            extractor.release();
            if (pfd != null) {
                try { pfd.close(); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Decodes a small snippet of audio (approx 1-2 seconds) from current extractor position.
     */
    private float[] decodeSnippet(MediaExtractor extractor, MediaCodec codec, MediaFormat format) {
        int targetSamples = 20000; // Aim for slightly more than 16000 to be safe
        ArrayList<Float> accumulator = new ArrayList<>();

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean outputDone = false;
        int timeoutUs = 2000;
        int retryCount = 0;

        int inputSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;

        while (!outputDone && accumulator.size() < targetSamples * 2 && retryCount < 50) {
            // Feed Input
            int inputIndex = codec.dequeueInputBuffer(timeoutUs);
            if (inputIndex >= 0) {
                ByteBuffer buffer = codec.getInputBuffer(inputIndex);
                assert buffer != null;
                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize < 0) {
                    codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                    extractor.advance();
                }
            }

            // Read Output
            int outputIndex = codec.dequeueOutputBuffer(info, timeoutUs);
            if (outputIndex >= 0) {
                ByteBuffer buffer = codec.getOutputBuffer(outputIndex);
                if (buffer != null) {
                    // Read data
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    ShortBuffer sb = buffer.asShortBuffer();
                    int remaining = sb.remaining();
                    short[] pcm = new short[remaining];
                    sb.get(pcm);

                    // Convert & Resample immediate chunk
                    float[] chunkFloat = convertAndResample(pcm, remaining, inputSampleRate, channelCount, null);
                    for (float f : chunkFloat) accumulator.add(f);
                }
                codec.releaseOutputBuffer(outputIndex, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputDone = true;
                }
                retryCount = 0; // Reset retry if we got data
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                retryCount++;
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = codec.getOutputFormat();
                inputSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            }
        }

        // Convert List to Array
        float[] result = new float[accumulator.size()];
        for (int i = 0; i < accumulator.size(); i++) result[i] = accumulator.get(i);
        return result;
    }

    private int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) return i;
        }
        return -1;
    }

    private List<String> loadLabels() {
        List<String> labelList = new ArrayList<>();
        try (InputStream is = context.getAssets().open(LABELS_FILE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    labelList.add(line.trim());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to load labels", e);
        }
        return labelList;
    }

    private String assetFilePath(String assetName) throws IOException {
        File file = new File(context.getCacheDir(), assetName);
        try (InputStream is = context.getAssets().open(assetName);
             FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }
        return file.getAbsolutePath();
    }
}