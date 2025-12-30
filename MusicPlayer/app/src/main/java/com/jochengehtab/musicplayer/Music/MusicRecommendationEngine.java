package com.jochengehtab.musicplayer.Music;

import com.jochengehtab.musicplayer.data.Track;
import java.util.List;

public class MusicRecommendationEngine {

    /**
     * Finds the track in the list that is most mathematically similar to the current track.
     */
    public static Track findNextSong(Track currentTrack, List<Track> allTracks) {
        float[] currentVector = currentTrack.getStyleVector();
        if (currentVector == null) return null;

        Track bestMatch = null;
        double maxSimilarity = -1.0;

        for (Track candidate : allTracks) {
            // 1. Skip the song that just played
            if (candidate.id == currentTrack.id) continue;

            // 2. Skip songs that haven't been analyzed yet
            float[] candidateVector = candidate.getStyleVector();
            if (candidateVector == null) continue;

            // 3. Calculate Similarity (Cosine Distance)
            double similarity = cosineSimilarity(currentVector, candidateVector);

            // 4. Find the highest score
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                bestMatch = candidate;
            }
        }
        return bestMatch;
    }

    private static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        // Assuming both are length 1024
        int length = Math.min(vectorA.length, vectorB.length);

        for (int i = 0; i < length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }

        if (normA == 0 || normB == 0) return 0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
