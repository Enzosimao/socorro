package br.com.socorro;

import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.util.concurrent.*;

final class SyncManager {
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final java.util.concurrent.atomic.AtomicInteger scheduled = new java.util.concurrent.atomic.AtomicInteger();
    static volatile boolean busy;
    static void flush(Context source, Runnable completion) {
        Context context = source.getApplicationContext();
        scheduled.incrementAndGet(); busy = true;
        worker.execute(() -> {
            SharedPreferences prefs = context.getSharedPreferences("socorro", Context.MODE_PRIVATE);
            try (Outbox db = new Outbox(context)) {
                for (int count = 0; count < 30; count++) {
                    Outbox.Entry entry = db.first();
                    if (entry == null) break;
                    prefs.edit().putLong("lastAttempt", System.currentTimeMillis()).apply();
                    try {
                        JSONObject request = new JSONObject(entry.body);
                        JSONObject response = ApiClient.post(entry.body);
                        ApiClient.validateConfirmation(response, request);
                        String remoteId = response.optString("id");
                        db.confirmed(entry, remoteId);
                        prefs.edit().putLong("lastSync", System.currentTimeMillis()).putString("lastSyncUser", request.optString("usuario"))
                                .putString("lastSyncId", remoteId).putString("syncError", "").putString("syncWarning", ApiClient.confirmationWarning(response, request)).putString("syncStatus", remoteId.isEmpty()?"Recebimento confirmado pela API":"Registro #" + remoteId + " confirmado pelo servidor").apply();
                    } catch (Exception error) {
                        db.failed(entry);
                        prefs.edit().putString("syncError", ApiClient.errorMessage(error)).putString("syncStatus", "Envio pendente. Dados preservados no celular.").apply();
                        break;
                    }
                }
            } catch (Exception error) {
                prefs.edit().putString("syncError", ApiClient.errorMessage(error)).apply();
            } finally {
                busy = scheduled.decrementAndGet() > 0;
                if (completion != null) main.post(completion);
            }
        });
    }
}
