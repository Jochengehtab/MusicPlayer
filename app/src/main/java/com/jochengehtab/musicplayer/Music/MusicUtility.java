package com.jochengehtab.musicplayer.Music;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;

import com.jochengehtab.musicplayer.Data.AppDatabase;
import com.jochengehtab.musicplayer.Data.PlaylistWithTracks;
import com.jochengehtab.musicplayer.Data.Track;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MusicUtility {
    private static final int HISTORY_SIZE = 10; // Remember last 10 songs
    private int currentIndex = 0;
    private boolean loopEnabled = false;
    private boolean mixEnabled = false;
    private final Context context;
    private final AppDatabase database;
    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelToken = new AtomicBoolean(false);
    private final MusicRecommendationEngine musicRecommendationEngine = new MusicRecommendationEngine();
    private final Consumer<String> updateBottomTitle;
    private final Consumer<Boolean> updateBottomPlayIcon;
    private final List<Track> playQueue = new ArrayList<>();
    private final LinkedList<Long> recentHistory = new LinkedList<>();

    public MusicUtility(Context context, AppDatabase database, Consumer<String> updateBottomTitle, Consumer<Boolean> updateBottomPlayIcon) {
        this.context = context;
        this.database = database;
        this.updateBottomTitle = updateBottomTitle;
        this.updateBottomPlayIcon = updateBottomPlayIcon;
        initMediaSession();
    }

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(context, "MusicUtilitySession");
        
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                resume();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                // Stop current handler to prevent the scheduled stop from firing later
                handler.removeCallbacksAndMessages(null);

                if (isInitialized() && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }

                updateBottomPlayIcon.accept(false);

                // Force next song logic
                findAndPlayNextSong(true);
            }

            @Override
            public void onSkipToPrevious() {
                // Stop any pending auto-next timers
                handler.removeCallbacksAndMessages(null);

                // Logic: Actually go to previous track
                if (currentIndex > 0) {
                    currentIndex--;
                    playCurrentQueueItem();
                } else {
                    // If we are at the very first song (index 0), just restart it
                    playCurrentQueueItem();
                }
            }
        });

        mediaSession.setActive(true);
    }

    public void playTrack(Track track, long... timespan) {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        } else {
            mediaPlayer.reset();
        }

        StringBuilder stringBuilder = new StringBuilder();
        for (Track elemnt : playQueue) {
            stringBuilder.append(elemnt.title);
            stringBuilder.append("; ");
        }
        Log.i("Play Queue", String.valueOf(stringBuilder));

        updateMediaSessionState(PlaybackStateCompat.STATE_PLAYING);

        // Update Metadata
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.duration)
                .build();
        mediaSession.setMetadata(metadata);

        addToHistory(track.id);
        cancelToken.set(false);

        long startMs = (timespan != null && timespan.length >= 1) ? timespan[0] : track.startTime;
        long endMs = (timespan != null && timespan.length >= 2) ? timespan[1] : track.endTime;

        mediaPlayer.setStartTime(startMs);
        mediaPlayer.setEndTime(endMs);
        mediaPlayer.setCurrentTrack(track);

        long durationMs = endMs - startMs;

        try {
            mediaPlayer.setDataSource(context, Uri.fromFile(new File(track.uri)));
            mediaPlayer.setOnPreparedListener(mp -> mp.seekTo((int) startMs));
            mediaPlayer.setOnSeekCompleteListener(mp -> {
                mp.start();
                scheduleStop(durationMs, (MediaPlayer) mp);
            });
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        updateBottomPlayIcon.accept(true);
    }

    private synchronized void playCurrentQueueItem(long... timespan) {
        if (cancelToken.get()) return;

        Track track;
        if (loopEnabled) {
            track = mediaPlayer.getCurrentTrack();
        } else {
            track = playQueue.get(currentIndex);
            updateBottomTitle.accept(track.title);
        }
        playTrack(track, timespan);
    }

    public void handleLooping() {
        loopEnabled = true;
    }

    public void handleMix(String playListName) {
        cancelToken.set(false);
        loopEnabled = false;
        if (isPlaying()) {
            mixEnabled = true;
            playQueue.clear();
            playQueue.add(mediaPlayer.getCurrentTrack());
            // Since the queue only has one item
            currentIndex = 0;
            findAndPlayNextSong(false);
        } else if (!playQueue.isEmpty()) {
            Collections.shuffle(playQueue);
            currentIndex = 0;
            playCurrentQueueItem();
        } else {
            executor.execute(() -> {
                PlaylistWithTracks playlistWithTracks = database.playlistDao().getPlaylistWithTracks(playListName);
                if (playlistWithTracks != null && !playlistWithTracks.tracks.isEmpty()) {
                    List<Track> tracks = playlistWithTracks.tracks;
                    Collections.shuffle(tracks);
                    handler.post(() -> {
                        playQueue.addAll(tracks);
                        currentIndex = 0;
                        playCurrentQueueItem();
                    });
                }
            });
        }
    }

    private void updateMediaSessionState(int state) {
        if (mediaSession == null) return;

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE
                );

        stateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void scheduleStop(long delayMs, MediaPlayer targetMp) {
        // Clear the handler
        handler.removeCallbacksAndMessages(null);

        // Stop the music exactly after delayMs is passed
        handler.postDelayed(() -> {
            if (mediaPlayer == targetMp && targetMp.isPlaying()) {
                targetMp.pause();
                updateBottomPlayIcon.accept(false);
                if (loopEnabled) {
                    // Replay same song
                    playCurrentQueueItem();
                } else {
                    findAndPlayNextSong(true);
                }
            }
        }, delayMs);
    }

    /**
     * @param playImmediately
     *   true = Normal behavior (End of song -> Play next).
     *   false = Mix start behavior (Just add to queue, don't stop current song).
     */
    private void findAndPlayNextSong(boolean playImmediately) {
        if (playQueue.isEmpty()) return;

        // Create a copy of the history to pass to the thread safely
        List<Long> historySnapshot = new ArrayList<>(recentHistory);

        executor.execute(() -> {
            List<Track> allTracks = database.trackDao().getAllTracks();

            Track nextTrack = musicRecommendationEngine.findNextSong(mediaPlayer.getCurrentTrack(), allTracks, historySnapshot);

            handler.post(() -> {
                if (nextTrack != null) {
                    playQueue.add(nextTrack);
                }

                // Only increment and play if requested.
                // Otherwise, we just successfully buffered the next song.
                if (playImmediately) {
                    currentIndex++;
                    playCurrentQueueItem();
                }
            });
        });
    }

    public synchronized void stopAndCancel() {
        cancelToken.set(true);
        handler.removeCallbacksAndMessages(null);
        if (isInitialized()) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.reset();
        }
        mixEnabled = false;
    }

    /**
     * This is required because stopAndCancel() no longer releases memory.
     */
    public synchronized void destroy() {
        cancelToken.set(true);
        handler.removeCallbacksAndMessages(null);
        if (mediaPlayer != null) {
            mediaPlayer.release(); // Actually free native memory
            mediaPlayer = null;
        }

        // Release Media Session
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
    }

    public void pause() {
        if (!isInitialized()) return;
        mediaPlayer.pause();
        mediaPlayer.setStartTime(mediaPlayer.getCurrentPosition());
        handler.removeCallbacksAndMessages(null);
        updateBottomPlayIcon.accept(false);

        // Update Session State
        updateMediaSessionState(PlaybackStateCompat.STATE_PAUSED);
    }

    public void resume() {
        if (!isInitialized()) return;

        // If the player is already prepared (just paused),
        // simply start it and schedule the stop.
        // This prevents re-buffering and prevents re-adding the song to history.
        long remainingTime = mediaPlayer.getEndTime() - mediaPlayer.getCurrentPosition();

        if (remainingTime > 0) {
            mediaPlayer.start();
            scheduleStop(remainingTime, mediaPlayer);
            updateBottomPlayIcon.accept(true);
        } else {
            // Edge case: If we resumed at the very end of the song, play next
            findAndPlayNextSong(true);
        }

        // Update Session State
        updateMediaSessionState(PlaybackStateCompat.STATE_PLAYING);
    }

    // Helper to manage history size
    private void addToHistory(long trackId) {

        // Check if the entry already exists in the history
        if (recentHistory.contains(trackId)) {
            return;
        }

        recentHistory.remove(trackId); // Move to end if played again
        recentHistory.add(trackId);

        // Keep history size limited
        if (recentHistory.size() > HISTORY_SIZE) {
            recentHistory.removeFirst(); // Remove oldest
        }
    }

    public boolean isPlaying() {
        return isInitialized() && mediaPlayer.isPlaying();
    }

    public boolean isLooping() {
        return loopEnabled;
    }

    public boolean isMixing() {
        return mixEnabled;
    }

    private boolean isInitialized() {
        return mediaPlayer != null;
    }
}