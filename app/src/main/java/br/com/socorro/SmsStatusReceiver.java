package br.com.socorro;

import android.app.Activity;
import android.content.*;

public class SmsStatusReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!"br.com.socorro.SMS_RESULT".equals(intent.getAction())) return;
        try (Outbox db = new Outbox(context)) { db.smsResult(intent.getLongExtra("sms", -1), getResultCode() == Activity.RESULT_OK); }
    }
}
