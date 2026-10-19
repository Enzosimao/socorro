package br.com.socorro;

import android.app.*;
import android.content.*;
import android.net.Uri;
import org.json.*;

final class AlertChannels {
    static String channel(SharedPreferences prefs) {
        String value = prefs.getString("alertChannel", "sms");
        return "whatsapp".equals(value) || "both".equals(value) ? value : "sms";
    }
    static boolean usesSms(SharedPreferences prefs) { return !channel(prefs).equals("whatsapp"); }
    static JSONArray pending(SharedPreferences prefs) {
        try { return new JSONArray(prefs.getString("whatsappPending", "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }
    static Uri whatsAppUri(String phone, String message) {
        String number = Contacts.phone(phone);
        if (!number.startsWith("+") && (number.length() == 10 || number.length() == 11)) number = "55" + number;
        return Uri.parse("https://wa.me/" + number.replace("+", "")).buildUpon().appendQueryParameter("text", message).build();
    }
    static void send(Context context, Outbox db, long event, JSONObject body, String reason) {
        SharedPreferences prefs = context.getSharedPreferences("socorro", Context.MODE_PRIVATE);
        if (usesSms(prefs)) SmsNotifier.send(context, db, event, body, reason);
        if (channel(prefs).equals("sms")) return;
        JSONArray contacts = Contacts.read(prefs);
        if (contacts.length() == 0) {
            db.event(0, 0, "WhatsApp indisponível", "Nenhum contato cadastrado", "Sem destinatários");
            return;
        }
        try {
            JSONArray queue = pending(prefs);
            queue.put(new JSONObject().put("event", event).put("at", System.currentTimeMillis())
                    .put("message", SmsNotifier.message(body, reason)).put("contacts", contacts));
            boolean saved = prefs.edit().putString("whatsappPending", queue.toString()).commit();
            db.event(0, 0, "WhatsApp", saved ? "Mensagem preparada para " + contacts.length() + " contato(s). Abra o aplicativo e confirme o envio no WhatsApp." : "Não foi possível salvar a mensagem para envio manual", saved ? "Aguardando envio manual" : "Falhou");
            if (!saved) return;
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel("whatsapp", "Alertas para enviar no WhatsApp", NotificationManager.IMPORTANCE_HIGH));
            Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent action = PendingIntent.getActivity(context, 3, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(context, "whatsapp").setSmallIcon(R.drawable.ic_shield)
                    .setContentTitle("WhatsApp · confirme o envio do alerta")
                    .setContentText("Abra o Socorro, escolha o contato e toque em Enviar no WhatsApp.")
                    .setStyle(new Notification.BigTextStyle().bigText("O alerta ainda não foi enviado pelo WhatsApp. Abra o Socorro e selecione cada contato para confirmar o envio."))
                    .setContentIntent(action).setAutoCancel(true).build();
            try { manager.notify(3, notification); } catch (SecurityException ignored) { }
        } catch (JSONException e) {
            db.event(0, 0, "WhatsApp", "Não foi possível preparar a mensagem", "Falhou");
        }
    }
    static void clear(Context context) {
        context.getSharedPreferences("socorro", Context.MODE_PRIVATE).edit().remove("whatsappPending").apply();
        context.getSystemService(NotificationManager.class).cancel(3);
    }
}
