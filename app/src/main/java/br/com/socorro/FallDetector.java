package br.com.socorro;

 
public final class FallDetector {
    private long rotationAt = -10000, freeFallAt = -10000, lastFall = -30000;
    private long candidateAt = -1, stillAt = -1;
    private boolean proximityAvailable, covered;
    private double altitudeDrop, peak;
    public void context(boolean available, boolean covered, double altitudeDrop) {
        this.proximityAvailable = available; this.covered = covered; this.altitudeDrop = altitudeDrop;
    }
    public void rotation(float x, float y, float z, long time) {
        double magnitude = Math.sqrt((double)x*x + (double)y*y + (double)z*z);
        if (Double.isFinite(magnitude) && magnitude > 2.5) rotationAt = time;
    }
    public double peakG() { return peak; }
    public boolean acceleration(float x, float y, float z, long time, boolean hasGyroscope) {
        double g = Math.sqrt((double)x*x + (double)y*y + (double)z*z) / 9.80665;
        if (!Double.isFinite(g)) return false;
        if (g < 0.5) freeFallAt = time;
        if (time - lastFall < 30000) return false;
        if (candidateAt >= 0) {
            peak = Math.max(peak, g);
            if (time - candidateAt > 5000 || time < candidateAt) { candidateAt = -1; stillAt = -1; }
            else {
                if (g >= 0.7 && g <= 1.3) {
                    if (stillAt < 0) stillAt = time;
                    if (time - stillAt >= 1000) {
                        candidateAt = -1; stillAt = -1; lastFall = time; return true;
                    }
                } else stillAt = -1;
                return false;
            }
        }
        boolean freeFall = time >= freeFallAt && time - freeFallAt <= 1500;
        boolean rotation = time >= rotationAt && time - rotationAt <= 1500;
        double threshold = hasGyroscope ? 3.0 : 3.5;
        
        if (proximityAvailable && !covered && !freeFall && altitudeDrop < 0.8) threshold = 4.5;
        if (g >= threshold && (!hasGyroscope || rotation)) { candidateAt = time; stillAt = -1; peak = g; }
        return false;
    }
}
