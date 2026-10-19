package br.com.socorro;

import android.content.*;
import android.location.Location;
import android.os.*;
import org.json.JSONObject;
import java.util.UUID;

final class TelemetryPayload {
    static synchronized JSONObject create(Context context, Location location, SensorReadings readings, int type, double impact) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("socorro", Context.MODE_PRIVATE);
        String name = prefs.getString("name", "").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Salve seu nome antes de enviar.");
        JSONObject body = new JSONObject();
        String device = prefs.getString("device", "");
        if (device.isEmpty()) { device = UUID.randomUUID().toString(); prefs.edit().putString("device", device).commit(); }
        String model = Build.MANUFACTURER + " " + Build.MODEL;
        body.put("usuario", name.substring(0, Math.min(150, name.length())));
        body.put("tipo_registro", type); body.put("device_id", device); body.put("modelo", model.substring(0, Math.min(100, model.length())));
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null && battery.getIntExtra(BatteryManager.EXTRA_SCALE, 0) > 0)
            body.put("bateria", Math.max(0, Math.min(100, 100.0 * battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) / battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100))));
        if (MonitorService.fresh(location)) {
            body.put("latitude", location.getLatitude()); body.put("longitude", location.getLongitude());
            if (location.hasSpeed() && Float.isFinite(location.getSpeed()) && location.getSpeed() >= 0) body.put("velocidade", location.getSpeed() * 3.6);
        }
        if (readings != null) {
            if (Float.isFinite(readings.pressure) && readings.pressure > 0) body.put("pressao", readings.pressure);
            if (Float.isFinite(readings.temperature)) body.put("temperatura", readings.temperature);
        }
        if (type == 2 && Double.isFinite(impact) && impact > 0) body.put("nivel_queda", impact);
        return body;
    }
}
