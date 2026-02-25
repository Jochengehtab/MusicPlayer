package com.jochengehtab.musicplayer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;

import com.jochengehtab.musicplayer.AudioClassifier.AudioClassifier;
import com.jochengehtab.musicplayer.Data.AppDatabase;
import com.jochengehtab.musicplayer.Data.Track;
import com.jochengehtab.musicplayer.Data.TrackDao;
import com.jochengehtab.musicplayer.MainActivity.MusicAnalysis;
import com.jochengehtab.musicplayer.MainActivity.MusicAnalysisCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;

public class MusicAnalysisTest {

    // We create fake versions of these
    @Mock AppDatabase database;
    @Mock TrackDao trackDao;
    @Mock AudioClassifier classifier;
    @Mock MusicAnalysisCallback callback;

    // With this executor we run it synchronously instead of
    // running it in the background
    ExecutorService immediateExecutor = mock(ExecutorService.class);

    private MusicAnalysis musicAnalysis;
    private AutoCloseable closeable;

    @Before
    public void setup() {
        closeable = MockitoAnnotations.openMocks(this);

        // Tell the fake database to return the fake DAO
        when(database.trackDao()).thenReturn(trackDao);

        musicAnalysis = new MusicAnalysis(database, immediateExecutor, classifier);

        // Trick: When executor.execute() is called, run the runnable immediately
        // This makes the asynchronous code synchronous for testing
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(immediateExecutor).execute(any());
    }

    @Test
    public void testAnalysis_ProcessUnanalyzedTracks() {
        // This vector is already analyzed so it should be skipped
        Track trackDone = new Track("path1", "Title 1", "Artist", "Album", 100L, 100L);
        trackDone.id = 1;
        trackDone.embeddingVector = "0.1,0.2,0.3";

        // Empty vector that should be analyzed
        Track trackTodo = new Track("path2", "Title 2", "Artist", "Album", 200L, 200L);
        trackTodo.id = 2;
        trackTodo.embeddingVector = null;

        // When the DB is asked for tracks we return our list
        when(trackDao.getAllTracks()).thenReturn(Arrays.asList(trackDone, trackTodo));

        // When classifier is called, return a dummy vector
        float[] dummyVector = new float[]{0.9f, 0.9f};
        when(classifier.getStyleEmbedding(any(Uri.class), any())).thenReturn(dummyVector);

        // Now we actually execute the action
        musicAnalysis.checkAndStartAnalysis(callback);

        // Now we need to verify everything
        verify(callback).onStarted();

        // Verify we only ran the classifier for Track 2
        // We expect getStyleEmbedding to be called exactly 1 time
        verify(classifier, times(1)).getStyleEmbedding(any(), any());

        // Verify we saved the result to the DB for Track 2
        verify(trackDao).updateTrackEmbedding(eq(2L), anyString());

        // Verify we did not save anything for Track 1
        verify(trackDao, never()).updateTrackEmbedding(eq(1L), anyString());

        // Verify that we finished
        verify(callback).onFinish();
    }

    @Test
    public void testAnalysis_DoNothingIfAllAnalyzed() {
        // This vector is already analyzed so it should be skipped-
        Track trackDone = new Track("path1", "Title 1", "Artist", "Album", 100L, 100L);
        trackDone.embeddingVector = "1.0,0.5";

        when(trackDao.getAllTracks()).thenReturn(Collections.singletonList(trackDone));

        // Now we actually execute the action
        musicAnalysis.checkAndStartAnalysis(callback);

        // We should finish immediately without starting classifier
        verify(classifier, never()).getStyleEmbedding(any(), any());
        verify(callback).onFinish();
    }

    @After
    public void tearDown() throws Exception {
        // Clean up resources after the test is done
        closeable.close();
    }
}