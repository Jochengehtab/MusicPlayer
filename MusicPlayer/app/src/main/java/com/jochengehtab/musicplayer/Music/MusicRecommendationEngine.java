package com.jochengehtab.musicplayer.Music;

import com.jochengehtab.musicplayer.data.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class MusicRecommendationEngine {

    private static final Random random = new Random();

    /**
     * Finds a suitable next song.
     * Strategy:
     * 1. Calculate similarity for ALL tracks.
     * 2. Sort by similarity (High to Low).
     * 3. Filter out songs currently in the 'recentHistory'.
     * 4. Pick a random song from the top 5 remaining candidates.
     */
    public static Track findNextSong(Track currentTrack, List<Track> allTracks, List<Long> recentHistory) {
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
        return candidates.get(randomIndex).track;
    }

    private static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        int length = Math.min(vectorA.length, vectorB.length);

        for (int i = 0; i < length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }

        if (normA == 0 || normB == 0) return 0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // Helper class to store track and its score temporarily
    private static class ScoredTrack {
        Track track;
        double score;

        ScoredTrack(Track track, double score) {
            this.track = track;
            this.score = score;
        }
    }
}