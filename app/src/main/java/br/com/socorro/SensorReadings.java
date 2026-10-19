package br.com.socorro;

import android.hardware.*;

final class SensorReadings {
    static final int[] TYPES = {Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_PROXIMITY,
            Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE, Sensor.TYPE_AMBIENT_TEMPERATURE};
    final boolean[] available = new boolean[6], listening = new boolean[6];
    final float[] acceleration = new float[3], rotation = new float[3];
    double g = Double.NaN, gyro = Double.NaN;
    float proximity = Float.NaN, light = Float.NaN, pressure = Float.NaN, temperature = Float.NaN;
    boolean covered, pressureListening;
    final String pressureSensorName, proximitySensorName;
    final boolean virtualProximity;
    double altitudeDrop;
    private float referencePressure = Float.NaN;
    private long pressureAt;
    SensorReadings(SensorManager manager) {
        for (int i = 0; i < TYPES.length; i++) available[i] = manager.getDefaultSensor(TYPES[i]) != null;
        Sensor barometer = manager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        pressureSensorName = barometer == null ? "" : barometer.getName();
        Sensor proximitySensor = sensor(manager, Sensor.TYPE_PROXIMITY);
        available[2] = proximitySensor != null;
        proximitySensorName = proximitySensor == null ? "" : proximitySensor.getName();
        virtualProximity = android.os.Build.MODEL.toUpperCase(java.util.Locale.ROOT).startsWith("SM-A346") || proximitySensorName.toLowerCase(java.util.Locale.ROOT).contains("virtual");
    }
    void accept(SensorEvent event) {
        if (event.values.length == 0) return;
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, acceleration, 0, 3);
                g = Math.sqrt((double)acceleration[0]*acceleration[0] + (double)acceleration[1]*acceleration[1] + (double)acceleration[2]*acceleration[2]) / SensorManager.GRAVITY_EARTH; break;
            case Sensor.TYPE_GYROSCOPE:
                System.arraycopy(event.values, 0, rotation, 0, 3);
                gyro = Math.sqrt((double)rotation[0]*rotation[0] + (double)rotation[1]*rotation[1] + (double)rotation[2]*rotation[2]); break;
            case Sensor.TYPE_PROXIMITY:
                acceptProximity(event.values[0], event.sensor.getMaximumRange()); break;
            case Sensor.TYPE_LIGHT: light = event.values[0]; break;
            case Sensor.TYPE_PRESSURE:
                acceptPressure(event.values[0], event.timestamp); break;
            case Sensor.TYPE_AMBIENT_TEMPERATURE: temperature = event.values[0]; break;
        }
    }
    static Sensor sensor(SensorManager manager, int type) {
        Sensor result = type == Sensor.TYPE_PROXIMITY ? manager.getDefaultSensor(type, true) : null;
        return result != null ? result : manager.getDefaultSensor(type);
    }
    boolean hasProximityReading() { return available[2] && listening[2] && Float.isFinite(proximity); }
    boolean canRefineDetection() { return hasProximityReading() && !virtualProximity; }
    void acceptProximity(float value, float maximumRange) {
        if (!Float.isFinite(value) || value < 0 || !Float.isFinite(maximumRange) || maximumRange <= 0) {
            proximity = Float.NaN; covered = false; return;
        }
        proximity = value; covered = value < Math.min(5, maximumRange);
    }
    void acceptPressure(float value, long timestamp) {
        if (!Float.isFinite(value) || value <= 0) return;
        pressure = value;
        if (Float.isNaN(referencePressure) || timestamp < pressureAt || timestamp - pressureAt > 3000000000L) {
            referencePressure = pressure; pressureAt = timestamp;
        }
        altitudeDrop = -SensorManager.getAltitude(referencePressure, pressure);
    }
}
