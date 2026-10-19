package br.com.socorro;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.location.*;
import android.os.*;
import org.json.JSONObject;
import java.util.UUID;
import java.util.concurrent.*;

public class MonitorService extends Service implements SensorEventListener, LocationListener {
    public static volatile boolean running;
    public static volatile long startedAt, tripId, alertDeadline, nextCheckIn, lastTickAt, lastAccelerationAt;
    static volatile SensorReadings currentReadings;
    public static volatile int alertReason;
    public static volatile Location latestLocation;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final FallDetector detector = new FallDetector();
    private final SafetyTimer timer = new SafetyTimer();
    private SensorManager sensors;
    private SensorReadings readings;
    private LocationManager locations;
    private PowerManager.WakeLock wakeLock;
    private SharedPreferences prefs;
    private Outbox outbox;
    private Location routePoint;
    private double impact;
    private boolean sending, destroyed, firstLocationSent;
    private long lastPointAt, lastNotificationSecond = -1, renewAt, heartbeatAt;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("socorro", MODE_PRIVATE);
        tripId = 0;
        outbox = new Outbox(this);
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("monitor", "Monitoramento", NotificationManager.IMPORTANCE_LOW));
        notifications.createNotificationChannel(new NotificationChannel("emergency", "Quedas e check-in", NotificationManager.IMPORTANCE_HIGH));
        try { startForeground(1, notification("Iniciando monitoramento", false)); }
        catch (SecurityException e) { prefs.edit().putString("status", "Conceda localização para iniciar").apply(); stopSelf(); return; }
        running = true; startedAt = SystemClock.elapsedRealtime(); latestLocation = null;
        tripId = outbox.startTrip(); alertDeadline = 0; alertReason = SafetyTimer.NONE;
        outbox.event(tripId, 0, "Monitoramento iniciado", "Sensores e localização ativados", "Local");
        sensors = (SensorManager)getSystemService(SENSOR_SERVICE);
        readings = new SensorReadings(sensors);
        PowerManager power = (PowerManager)getSystemService(POWER_SERVICE);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "socorro:monitor");
        wakeLock.setReferenceCounted(false);wakeLock.acquire(600000);renewAt=SystemClock.elapsedRealtime()+300000;
        currentReadings=readings;lastAccelerationAt=0;
        for (int i=0;i<SensorReadings.TYPES.length;i++) {
            int type=SensorReadings.TYPES[i];Sensor sensor=SensorReadings.sensor(sensors,type);
            if(sensor!=null)readings.listening[i]=sensors.registerListener(this,sensor,type==Sensor.TYPE_ACCELEROMETER||type==Sensor.TYPE_GYROSCOPE?SensorManager.SENSOR_DELAY_GAME:SensorManager.SENSOR_DELAY_NORMAL);
        }
        readings.pressureListening=readings.listening[4];
        locations = (LocationManager)getSystemService(LOCATION_SERVICE);
        try {
            boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            for (String provider : locations.getProviders(true)) {
                if (!provider.equals(LocationManager.GPS_PROVIDER) && !provider.equals(LocationManager.NETWORK_PROVIDER)) continue;
                if (provider.equals(LocationManager.GPS_PROVIDER) && !fine) continue;
                locations.requestLocationUpdates(provider, 5000, 5, this);
                Location last = locations.getLastKnownLocation(provider);
                if (last != null && fresh(last)) onLocationChanged(last);
            }
        } catch (SecurityException e) { setStatus("Localização sem permissão"); }
        scheduleCheckIn();
        setStatus(readings.listening[0] ? "Monitoramento ativo, inclusive com tela apagada" : "Acelerômetro sem leitura ativa. SOS manual disponível.");
        handler.post(clock);
        handler.postDelayed(telemetry, 60000);
        if (!firstLocationSent) send(1, 0);
        else flush();
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (!running || intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if ("STOP".equals(action)) { stopSelf(); return START_NOT_STICKY; }
        if ("CANCEL".equals(action)) cancelAlert();
        else if ("SOS".equals(action)) beginAlert(SafetyTimer.SOS, 0);
        else if ("SETTINGS".equals(action)) {
            if (timer.reason() == SafetyTimer.CHECK_IN && prefs.getInt("checkInMinutes",30) == 0) cancelAlert();
            scheduleCheckIn();
        }
        else if ("RETRY".equals(action)) flush();
        return START_NOT_STICKY;
    }
    private void scheduleCheckIn() {
        int minutes = prefs.getInt("checkInMinutes", 30);
        nextCheckIn = minutes > 0 ? SystemClock.elapsedRealtime() + minutes * 60000L : 0;
    }
    private final Runnable clock = new Runnable() {
        @Override public void run() {
            if (destroyed || !running) return;
            long now = SystemClock.elapsedRealtime();lastTickAt=now;
            if (now >= renewAt) {
                wakeLock.acquire(600000); renewAt = now + 300000;
            }
            if (now >= heartbeatAt) { outbox.heartbeat(tripId); heartbeatAt = now + 30000; }
            if (!timer.active() && nextCheckIn > 0 && now >= nextCheckIn) beginAlert(SafetyTimer.CHECK_IN, 0);
            if (timer.active()) {
                if (timer.expired(now)) {
                    int reason = timer.reason(); timer.cancel(); alertDeadline = 0; alertReason = 0;
                    getSystemService(NotificationManager.class).cancel(2);
                    send(2, reason); scheduleCheckIn();
                } else {
                    int seconds = timer.seconds(now);
                    if (seconds != lastNotificationSecond) {
                        getSystemService(NotificationManager.class).notify(2, notification(reasonTitle(timer.reason()) + " · " + seconds + " s para cancelar", true));
                        lastNotificationSecond = seconds;
                    }
                }
            }
            handler.postDelayed(this, 250);
        }
    };
    private final Runnable telemetry = new Runnable() {
        @Override public void run() {
            if (!timer.active()) send(1, 0);
            handler.postDelayed(this, 60000);
        }
    };
    private void beginAlert(int reason, double g) {
        if (!timer.begin(reason, SystemClock.elapsedRealtime())) return;
        impact = g; alertDeadline = timer.deadline(); alertReason = reason; lastNotificationSecond = -1;
        outbox.event(tripId, 0, reason == SafetyTimer.CHECK_IN ? "Check-in de segurança" : reasonTitle(reason), "Aguardando cancelamento por 30 segundos", "Contagem iniciada");
        Vibrator vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200, 500}, -1));
    }
    private void cancelAlert() {
        if (timer.active()) {
            String reason = reasonTitle(timer.reason()); timer.cancel(); alertDeadline = 0; alertReason = 0;
            getSystemService(NotificationManager.class).cancel(2);
            outbox.event(tripId, 0, "Alerta cancelado", reason + ". Usuário confirmou que está bem.", "Cancelado");
            setStatus("Monitoramento ativo"); scheduleCheckIn();
        } else {
            scheduleCheckIn();
            outbox.event(tripId, 0, "Check-in confirmado", "Usuário informou que está bem", "Local");
        }
    }
    static String reasonTitle(int reason) {
        if (reason == SafetyTimer.FALL) return "Possível queda detectada";
        if (reason == SafetyTimer.CHECK_IN) return "Check-in sem resposta";
        return "SOS manual";
    }
    static boolean fresh(Location l) {
        if (l == null) return false;
        long age = SystemClock.elapsedRealtimeNanos() - l.getElapsedRealtimeNanos();
        return age >= 0 && age < 120000000000L;
    }
    @Override public void onSensorChanged(SensorEvent event) {
        if (!running || readings == null) return;
        readings.accept(event); long now = event.timestamp / 1000000L;
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) detector.rotation(event.values[0], event.values[1], event.values[2], now);
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            lastAccelerationAt=SystemClock.elapsedRealtime();
            detector.context(readings.canRefineDetection(), readings.covered, readings.altitudeDrop);
            if (detector.acceleration(event.values[0], event.values[1], event.values[2], now, readings.listening[1])) beginAlert(SafetyTimer.FALL, detector.peakG());
        }
    }
    private JSONObject payload(int type) throws Exception {
        return TelemetryPayload.create(this, latestLocation, readings, type, impact);
    }
    private void send(int type, int reason) {
        JSONObject body;
        long event;
        try {
            body = payload(type);
            String title = type == 2 ? reasonTitle(reason) : "Registro do trajeto";
            String detail = body.has("latitude") ? body.optDouble("latitude") + ", " + body.optDouble("longitude") : "Sem localização recente";
            if (type == 2 && impact > 0) detail += String.format(java.util.Locale.getDefault(), " · impacto %.1f G", impact);
            event = outbox.add(type, body.toString(), tripId, title, detail);
        } catch (Exception e) {
            setStatus("Não foi possível salvar o registro no aparelho");
            if (type == 2) {
                try { body = payload(type); event = outbox.event(tripId, 2, reasonTitle(reason), "Falha ao salvar para o servidor", "Falhou"); }
                catch (Exception ignored) { return; }
                AlertChannels.send(this, outbox, event, body, reasonTitle(reason));
            }
            return;
        }
        if (type == 2) { AlertChannels.send(this, outbox, event, body, reasonTitle(reason)); setStatus("Alerta registrado no aparelho. Enviando ao servidor."); }
        flush();
    }
    private void flush() {
        if (sending || destroyed) return;
        sending = true;
        SyncManager.flush(this, () -> {
            sending = false;
            if (destroyed) return;
            handler.removeCallbacks(retry);
            if (outbox.pendingCount() > 0) handler.postDelayed(retry, 60000);
        });
    }
    private final Runnable retry = () -> flush();
    private Notification notification(String message, boolean emergency) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent cancel = PendingIntent.getService(this, 1, new Intent(this, MonitorService.class).setAction("CANCEL"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this, emergency ? "emergency" : "monitor")
                .setSmallIcon(R.drawable.ic_shield).setContentTitle(emergency ? "Socorro · você está bem?" : "Socorro")
                .setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(open)
                .setOngoing(true).setOnlyAlertOnce(true).setCategory(emergency ? Notification.CATEGORY_ALARM : Notification.CATEGORY_SERVICE);
        if (!emergency) {
            PendingIntent stop=PendingIntent.getService(this,4,new Intent(this,MonitorService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            b.addAction(new Notification.Action.Builder(null,"Encerrar monitoramento",stop).build());
        }
        b.setVisibility(Notification.VISIBILITY_PUBLIC);
        if (emergency) b.addAction(new Notification.Action.Builder(null, "Estou bem · cancelar", cancel).build());
        return b.build();
    }
    private void setStatus(String message) {
        prefs.edit().putString("status", message).apply();
        if (running) getSystemService(NotificationManager.class).notify(1, notification(message, false));
    }
    @Override public void onLocationChanged(Location location) {
        if (!fresh(location) || destroyed) return;
        Location previous = latestLocation;
        if (previous != null && location.getElapsedRealtimeNanos() < previous.getElapsedRealtimeNanos()) return;
        latestLocation = new Location(location);
        long now = SystemClock.elapsedRealtime();
        if (location.hasAccuracy() && location.getAccuracy() <= 100 && now - lastPointAt >= 10000) {
            float distance = routePoint == null ? 0 : routePoint.distanceTo(location);
            
            float uncertainty = routePoint == null ? 0 : Math.max(5, Math.max(routePoint.getAccuracy(), location.getAccuracy()));
            if (routePoint == null || distance >= uncertainty || now - lastPointAt >= 60000) {
                outbox.point(tripId, location, distance >= uncertainty ? distance : 0);
                routePoint = new Location(location); lastPointAt = now;
            }
        }
        WeatherClient.refresh(this, location, false);
        if (!firstLocationSent) { firstLocationSent = true; send(1, 0); }
    }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        destroyed = true;
        if (timer.active()) outbox.event(tripId, 0, "Alerta cancelado", "Monitoramento encerrado pelo usuário ou sistema", "Cancelado");
        currentReadings=null;lastTickAt=0;lastAccelerationAt=0;
        timer.cancel(); running = false; alertDeadline = 0; alertReason = 0; nextCheckIn = 0;
        handler.removeCallbacksAndMessages(null);
        if (sensors != null) sensors.unregisterListener(this);
        if (locations != null) locations.removeUpdates(this);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        getSystemService(NotificationManager.class).cancel(2);
        if (tripId > 0) outbox.finishTrip(tripId);
        network.execute(() -> outbox.close()); network.shutdown();
        prefs.edit().putString("status", "Monitoramento parado").apply();
        super.onDestroy();
    }
}
