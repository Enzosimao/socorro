package br.com.socorro;

 
public final class SafetyTimer {
    public static final int NONE = 0, FALL = 1, SOS = 2, CHECK_IN = 3;
    private long deadline;
    private int reason;
    public boolean begin(int reason, long now) {
        if (active() || reason < FALL || reason > CHECK_IN) return false;
        this.reason = reason; deadline = now + 30000; return true;
    }
    public boolean active() { return reason != NONE; }
    public int reason() { return reason; }
    public long deadline() { return deadline; }
    public int seconds(long now) { return active() ? (int)Math.max(0, (deadline - now + 999) / 1000) : 0; }
    public boolean expired(long now) { return active() && now >= deadline; }
    public void cancel() { reason = NONE; deadline = 0; }
}
