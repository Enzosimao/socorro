package br.com.socorro;

import android.content.*;
import android.location.Location;
import android.os.SystemClock;
import org.json.*;
import java.util.Locale;
import java.util.concurrent.*;

final class WeatherClient {
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static boolean busy;
    private static long attempt = -60000;
    static synchronized void refresh(Context context, Location location, boolean force) {
        if (!MonitorService.fresh(location)) return;
        SharedPreferences p = context.getApplicationContext().getSharedPreferences("socorro", Context.MODE_PRIVATE);
        float[] distance = new float[1];
        Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                Double.longBitsToDouble(p.getLong("weatherLat", 0)), Double.longBitsToDouble(p.getLong("weatherLon", 0)), distance);
        long now = SystemClock.elapsedRealtime();
        if (busy || now - attempt < 30000 || !force && System.currentTimeMillis() - p.getLong("weatherAt", 0) < 900000 && distance[0] < 5000) return;
        busy = true; attempt = now;
        double lat = location.getLatitude(), lon = location.getLongitude();
        p.edit().putBoolean("weatherLoading", true).apply();
        worker.execute(() -> {
            try {
                String url = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f&current=temperature_2m,precipitation,weather_code,wind_speed_10m,wind_gusts_10m&hourly=precipitation_probability&forecast_hours=3&timezone=auto", lat, lon);
                JSONObject json = ApiClient.get(url), current = json.getJSONObject("current");
                double probability = 0; int validHours = 0;
                JSONArray hours = json.getJSONObject("hourly").getJSONArray("precipitation_probability");
                for (int i = 0; i < hours.length(); i++) if (!hours.isNull(i)) {
                    probability = Math.max(probability, hours.getDouble(i)); validHours++;
                }
                if (validHours == 0) throw new java.io.IOException("Previsão indisponível");
                double temp = current.getDouble("temperature_2m"), rain = current.getDouble("precipitation"), wind = current.getDouble("wind_speed_10m"), gust = current.getDouble("wind_gusts_10m");
                int code = current.getInt("weather_code");
                p.edit().putString("weatherTemp", String.format(Locale.getDefault(), "%.0f°", temp))
                        .putString("weatherDescription", WeatherRisk.description(code))
                        .putString("weatherDetail", String.format(Locale.getDefault(), "Chuva %.0f%%  ·  vento %.0f km/h", probability, wind))
                        .putString("weatherWarning", WeatherRisk.warning(rain, wind, gust, probability, code))
                        .putBoolean("weatherRisk", rain > 0 || probability >= 70 || wind >= 40 || gust >= 50 || code >= 95 || code == 45 || code == 48)
                        .putLong("weatherAt", System.currentTimeMillis()).putLong("weatherLat", Double.doubleToLongBits(lat))
                        .putLong("weatherLon", Double.doubleToLongBits(lon)).putString("weatherError", "").apply();
            } catch (Exception e) { p.edit().putString("weatherError", "Não foi possível atualizar o clima. Verifique a conexão.").apply(); }
            finally { synchronized (WeatherClient.class) { busy = false; } p.edit().putBoolean("weatherLoading", false).apply(); }
        });
    }
}
