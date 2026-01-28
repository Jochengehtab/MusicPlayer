package com.jochengehtab.musicplayer.Music;

import android.util.Log;

import com.jochengehtab.musicplayer.data.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class MusicRecommendationEngine {

    private record ScoredTrack(Track track, double score) {}

    private final Random random = new Random();

    /**
     * Finds a suitable next song.
     * Strategy:
     * 1. Calculate similarity for ALL tracks.
     * 2. Sort by similarity (High to Low).
     * 3. Filter out songs currently in the 'recentHistory'.
     * 4. Pick a random song from the top 5 remaining candidates.
     */
    public Track findNextSong(Track currentTrack, List<Track> allTracks, List<Long> recentHistory) {
        float[] currentVector = currentTrack.getStyleVector();
        if (currentVector == null) return null;

        List<ScoredTrack> scoredTracks = new ArrayList<>();

        // 1. Score all tracks
        for (Track candidate : allTracks) {
            // Skip the song that just played
            if (candidate.id == currentTrack.id) continue;

            float[] candidateVector = candidate.getStyleVector();
            if (candidateVector == null) continue;

            double similarity = cosineSimilarity(currentVector, candidateVector);
            scoredTracks.add(new ScoredTrack(candidate, similarity));
        }

        // 2. Sort by Similarity (Highest first)
        scoredTracks.sort((o1, o2) -> Double.compare(o2.score, o1.score));

        // 3. Filter out history (Short-term memory)
        // We filter out tracks that are in the history list to prevent immediate loops
        List<ScoredTrack> candidates = scoredTracks.stream()
                .filter(st -> !recentHistory.contains(st.track.id))
                .collect(Collectors.toList());

        // Fallback: If we filtered everything out (small library), use the full list
        if (candidates.isEmpty()) {
            candidates = scoredTracks;
        }

        // 4. Select from Top N (e.g., Top 5)
        // This adds "Flavor" so it's not always the exact same path
        int poolSize = Math.min(candidates.size(), 5);
        if (poolSize == 0) return null;

        // Pick a random index from 0 to poolSize
        int randomIndex = random.nextInt(poolSize);
        Log.i("Next Song", candidates.get(randomIndex).track.title);
        for (ScoredTrack t : candidates) {
            Log.i("Candidates", t.track.title + " Similarity" + t.score);
        }
        return candidates.get(randomIndex).track;
    }

    // If you implement the re-normalization in AudioClassifier, use this:
    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        // We assume vectors are already normalized to length 1.0
        // This removes the need for expensive Math.sqrt calls during the loop
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
        }
        return dotProduct;
    }
}