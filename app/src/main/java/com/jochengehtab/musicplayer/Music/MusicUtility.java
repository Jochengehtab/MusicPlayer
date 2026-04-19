package com.jochengehtab.musicplayer.Music;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.widget.Toast;

import com.jochengehtab.musicplayer.Data.Track;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MusicUtility {
    private static final int HISTORY_SIZE = 10; // Remember last 10 songs
    private int currentIndex = 0;
    private boolean loopEnabled = true;
    private boolean mixEnabled = false;
    private final Context context;
    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelToken = new AtomicBoolean(false);
    private final MusicRecommendationEngine musicRecommendationEngine = new MusicRecommendationEngine();
    private final Consumer<String> updateBottomTitle;
    private final Consumer<Boolean> updateBottomPlayIcon;
    private final List<Track> playQueue = new ArrayList<>();
    private final LinkedList<Long> recentHistory = new LinkedList<>();
    private final LinkedList<Integer> playbackHistory = new LinkedList<>();
    private PlayingMode playingMode = PlayingMode.FORWARD_THROW_PLAYLIST;

    public MusicUtility(Context context, Consumer<String> updateBottomTitle, Consumer<Boolean> updateBottomPlayIcon) {
        this.context = context;
        this.updateBottomTitle = updateBottomTitle;
        this.updateBottomPlayIcon = updateBottomPlayIcon;
        initMediaSession();
    }

    public void playQueue(List<Track> playlist, int startIndex) {
        playbackHistory.clear();
        playQueue.clear();
        playQueue.addAll(playlist);
        this.currentIndex = startIndex;

        playCurrentQueueItem();
    }

    public void skipToPrevious() {
        // Stop any pending auto-next timers
        handler.removeCallbacksAndMessages(null);

        final int playbackHistorySize = playbackHistory.size();
        if (currentIndex > 0) {
            currentIndex--;
        } else if (playbackHistorySize > 1 && currentIndex != 0) {
            playbackHistory.removeLast();
            // Get previous song and remove it so playCurrentQueueItem can re-add it cleanly
            currentIndex = playbackHistory.removeLast();
        } else {
            // If we are here then we must be at the start of the playlist
            currentIndex = playQueue.size() - 1;
        }

        if (!playQueue.isEmpty()) playCurrentQueueItem();
    }

    public void skipToNext() {
        // Stop current handler to prevent the scheduled stop from firing later
        handler.removeCallbacksAndMessages(null);
        if (playQueue.size() > 1 && currentIndex + 1 < playQueue.size()) {
            currentIndex++;
            playCurrentQueueItem();
            return;
        }

        // Check if we are at the end of the playlist
        // If that is the case we start at the beginning of the play queue
        if (currentIndex == playQueue.size() - 1) {
            currentIndex = 0;
            playCurrentQueueItem();
        }
    }

    public void playTrack(Track track, long... timespan) {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        } else {
            mediaPlayer.reset();
        }

        handleMediaMetaData(track);

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

    private void handleMediaMetaData(Track track) {
        updateMediaSessionState(PlaybackStateCompat.STATE_PLAYING);

        // Update Metadata
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.duration)
                .build();
        mediaSession.setMetadata(metadata);
    }

    private synchronized void playCurrentQueueItem(long... timespan) {
        Track track = playQueue.get(currentIndex);
        updateBottomTitle.accept(track.title);
        if (currentIndex + 1 < playQueue.size()) playbackHistory.add(currentIndex);

        if (!loopEnabled) {
            track = musicRecommendationEngine.findNextSong(playQueue.get(currentIndex), playQueue, recentHistory);
            playQueue.add(track);
        }
        playTrack(track, timespan);
    }

    private void scheduleStop(long delayMs, MediaPlayer targetMp) {
        // Clear the handler
        handler.removeCallbacksAndMessages(null);

        // Stop the music exactly after delayMs is passed
        handler.postDelayed(() -> {
            if (mediaPlayer == targetMp) {
                targetMp.pause();
                updateBottomPlayIcon.accept(false);
                if (!loopEnabled) {
                    currentIndex++;
                }
                playCurrentQueueItem();
            }
        }, delayMs);
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
            playCurrentQueueItem();
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

    public void handleMix() {
        loopEnabled = false;
    }

    public void handleLooping() {
        loopEnabled = true;
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
                skipToNext();
            }

            @Override
            public void onSkipToPrevious() {
                skipToPrevious();
            }
        });

        mediaSession.setActive(true);
    }

    private void updateMediaSessionState(int state) {
        if (mediaSession == null) return;

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE
                );

        stateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
        mediaSession.setPlaybackState(stateBuilder.build());
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