package net.shasankp000.PathFinding;

/** Pure bounded-liveness state used by each navigation session. */
final class ReplanProgressTracker {
    private double bestDistance;
    private double progressCheckpoint;
    private int consecutiveWithoutProgress;
    private int total;
    private ReplanReason lastReason = ReplanReason.INITIAL;

    ReplanProgressTracker(double initialDistance) {
        bestDistance = initialDistance;
        progressCheckpoint = initialDistance;
    }

    boolean record(double currentDistance, ReplanReason reason) {
        total++;
        lastReason = reason;
        bestDistance = Math.min(bestDistance, currentDistance);
        if (progressCheckpoint - bestDistance >= 1.0) {
            progressCheckpoint = bestDistance;
            consecutiveWithoutProgress = 0;
        } else {
            consecutiveWithoutProgress++;
        }
        return consecutiveWithoutProgress >= 4;
    }

    double bestDistance() { return bestDistance; }
    int consecutiveWithoutProgress() { return consecutiveWithoutProgress; }
    int total() { return total; }
    ReplanReason lastReason() { return lastReason; }
}
