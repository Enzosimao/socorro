package br.com.socorro;

import android.Manifest;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;
import org.json.*;
import java.util.*;

final class SmsNotifier {
    static String message(JSONObject body, String reason) {
        String message = "SOCORRO: " + body.optString("usuario") + ". " + reason + ". Não houve cancelamento em 30 segundos. ";
        if (body.has("latitude") && body.has("longitude")) message += "Local: https://maps.google.com/?q=" + body.optDouble("latitude") + "," + body.optDouble("longitude");
        else message += "Localização atual indisponível.";
        return message;
    }
    static void send(Context context, Outbox db, long event, JSONObject body, String reason) {
        SharedPreferences p = context.getSharedPreferences("socorro", Context.MODE_PRIVATE);
        JSONArray contacts = Contacts.read(p);
        String unavailable = !p.getBoolean("smsEnabled", false) ? "SMS desativado" :
                context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ? "Sem permissão de SMS" :
                !context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY) ? "Aparelho sem telefonia" : null;
        String message = message(body, reason);
        for (int i = 0; i < contacts.length(); i++) {
            JSONObject contact = contacts.optJSONObject(i);
            if (contact == null) continue;
            String name = contact.optString("name"), phone = contact.optString("phone");
            if (unavailable != null) { db.sms(event, name, 0, unavailable); continue; }
            long row = 0;
            try {
                SmsManager manager = Build.VERSION.SDK_INT >= 31 ? context.getSystemService(SmsManager.class) : SmsManager.getDefault();
                if (manager == null) throw new IllegalStateException("Telefonia indisponível");
                ArrayList<String> parts = manager.divideMessage(message);
                row = db.sms(event, name, parts.size(), "Aguardando operadora");
                ArrayList<PendingIntent> results = new ArrayList<>();
                for (int part = 0; part < parts.size(); part++) {
                    Intent intent = new Intent(context, SmsStatusReceiver.class).setAction("br.com.socorro.SMS_RESULT")
                            .setData(android.net.Uri.parse("socorro://sms/" + row + "/" + part)).putExtra("sms", row);
                    results.add(PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
                }
                manager.sendMultipartTextMessage(phone, null, parts, results, null);
            } catch (Exception e) {
                if (row > 0) db.smsResult(row, false); else db.sms(event, name, 0, "Falha ao iniciar SMS");
            }
        }
        if (contacts.length() == 0) db.sms(event, "Contatos", 0, "Nenhum contato cadastrado");
    }
}
