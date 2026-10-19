package br.com.socorro;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.location.Location;
import org.json.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

 
final class Outbox extends SQLiteOpenHelper {
    static final AtomicLong revision = new AtomicLong();
    private final Context context;
    Outbox(Context context) { this(context, "socorro-outbox.db"); }
    Outbox(Context context, String name) { super(context, name, null, 3); this.context = context.getApplicationContext(); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE pending (id INTEGER PRIMARY KEY AUTOINCREMENT, type INTEGER NOT NULL, body TEXT NOT NULL, event_id INTEGER DEFAULT 0)");
        createHistory(db);
    }
    private void createHistory(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, trip INTEGER, title TEXT, detail TEXT, kind INTEGER, state TEXT, remote_id TEXT, payload TEXT)");
        db.execSQL("CREATE TABLE trips (id INTEGER PRIMARY KEY AUTOINCREMENT, start INTEGER, finish INTEGER DEFAULT 0, last_at INTEGER, distance REAL DEFAULT 0)");
        db.execSQL("CREATE TABLE points (id INTEGER PRIMARY KEY AUTOINCREMENT, trip INTEGER, at INTEGER, lat REAL, lon REAL, accuracy REAL)");
        db.execSQL("CREATE INDEX points_trip ON points(trip,id)");
        db.execSQL("CREATE TABLE sms (id INTEGER PRIMARY KEY AUTOINCREMENT, event_id INTEGER, contact TEXT, expected INTEGER, done INTEGER DEFAULT 0, failed INTEGER DEFAULT 0, state TEXT)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE pending ADD COLUMN event_id INTEGER DEFAULT 0"); createHistory(db);
            try (Cursor c = db.rawQuery("SELECT id,type FROM pending", null)) {
                while (c.moveToNext()) {
                    ContentValues v = new ContentValues(); v.put("at", System.currentTimeMillis()); v.put("trip", 0);
                    v.put("kind", c.getInt(1)); v.put("title", "Registro pendente da versão anterior");
                    v.put("detail", "Importado na atualização; data original não informada"); v.put("state", "Pendente");
                    long event = db.insertOrThrow("events", null, v);
                    db.execSQL("UPDATE pending SET event_id=? WHERE id=?", new Object[]{event,c.getLong(0)});
                }
            }
            String previous = context.getSharedPreferences("socorro", Context.MODE_PRIVATE).getString("history", "");
            if (!previous.isEmpty()) db.execSQL("INSERT INTO events(at,trip,title,detail,kind,state) VALUES(?,0,?,?,0,'Local')",
                    new Object[]{System.currentTimeMillis(), "Histórico da versão anterior", previous});
        }
        if (oldVersion >= 2 && oldVersion < 3) {
            db.execSQL("ALTER TABLE events ADD COLUMN payload TEXT");
        }
        if (oldVersion < 3) db.execSQL("UPDATE events SET payload=(SELECT body FROM pending WHERE pending.event_id=events.id LIMIT 1)");
    }
    long event(long trip, int kind, String title, String detail, String state) {
        ContentValues v = new ContentValues(); v.put("at", System.currentTimeMillis()); v.put("trip", trip);
        v.put("kind", kind); v.put("title", title); v.put("detail", detail); v.put("state", state);
        long id = getWritableDatabase().insertOrThrow("events", null, v); revision.incrementAndGet(); return id;
    }
    long add(int type, String body, long trip, String title, String detail) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            long event = event(trip, type, title, detail, "Pendente");
            ContentValues snapshot = new ContentValues(); snapshot.put("payload", body); db.update("events", snapshot, "id=?", new String[]{"" + event});
            ContentValues v = new ContentValues(); v.put("type", type); v.put("body", body); v.put("event_id", event);
            db.insertOrThrow("pending", null, v); db.setTransactionSuccessful(); return event;
        } finally { db.endTransaction(); }
    }
    Entry first() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,type,body,event_id FROM pending ORDER BY type DESC,id LIMIT 1", null)) {
            return c.moveToFirst() ? new Entry(c.getLong(0), c.getInt(1), c.getString(2), c.getLong(3)) : null;
        }
    }
    void confirmed(Entry entry, String remoteId) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            ContentValues v = new ContentValues(); v.put("state", "Confirmado"); v.put("remote_id", remoteId);
            db.update("events", v, "id=?", new String[]{"" + entry.event});
            db.delete("pending", "id=?", new String[]{"" + entry.id}); db.setTransactionSuccessful();
        } finally { db.endTransaction(); revision.incrementAndGet(); }
    }
    void failed(Entry entry) {
        ContentValues v = new ContentValues(); v.put("state", "Aguardando reenvio");
        getWritableDatabase().update("events", v, "id=?", new String[]{"" + entry.event}); revision.incrementAndGet();
    }
    int pendingCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM pending", null)) { c.moveToFirst(); return c.getInt(0); }
    }
    long startTrip() {
        finishInterrupted();
        ContentValues v = new ContentValues(); long now = System.currentTimeMillis(); v.put("start", now); v.put("last_at", now);
        long id = getWritableDatabase().insertOrThrow("trips", null, v); revision.incrementAndGet(); return id;
    }
    void finishInterrupted() { getWritableDatabase().execSQL("UPDATE trips SET finish=last_at WHERE finish=0"); revision.incrementAndGet(); }
    void heartbeat(long trip) {
        ContentValues v = new ContentValues(); v.put("last_at", System.currentTimeMillis());
        getWritableDatabase().update("trips", v, "id=?", new String[]{"" + trip});
    }
    void finishTrip(long trip) {
        ContentValues v = new ContentValues(); long now = System.currentTimeMillis(); v.put("finish", now); v.put("last_at", now);
        getWritableDatabase().update("trips", v, "id=?", new String[]{"" + trip}); revision.incrementAndGet();
    }
    void point(long trip, Location location, float distance) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            ContentValues v = new ContentValues(); v.put("trip", trip); v.put("at", location.getTime());
            v.put("lat", location.getLatitude()); v.put("lon", location.getLongitude()); v.put("accuracy", location.getAccuracy());
            db.insertOrThrow("points", null, v);
            db.execSQL("UPDATE trips SET distance=distance+?,last_at=? WHERE id=?", new Object[]{distance, System.currentTimeMillis(), trip});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); revision.incrementAndGet(); }
    }
    JSONArray points(long trip) {
        JSONArray result = new JSONArray(); int count;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM points WHERE trip=?", new String[]{"" + trip})) { c.moveToFirst(); count = c.getInt(0); }
        int stride = Math.max(1, (int)Math.ceil(count / 1000.0)); int index = 0;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT lat,lon FROM points WHERE trip=? ORDER BY id", new String[]{"" + trip})) {
            while (c.moveToNext()) { if (index++ % stride == 0 || index == count) result.put(new JSONArray(Arrays.asList(c.getDouble(0),c.getDouble(1)))); }
        }
        return result;
    }
    List<Event> events(boolean alerts) {
        List<Event> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,at,trip,title,detail,kind,state,remote_id,payload FROM events " + (alerts ? "WHERE kind=2 " : "") + "ORDER BY id DESC LIMIT 120", null)) {
            while (c.moveToNext()) rows.add(new Event(c));
        }
        return rows;
    }
    List<Trip> trips() {
        List<Trip> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,start,finish,last_at,distance,(SELECT COUNT(*) FROM points WHERE trip=trips.id),(SELECT COUNT(*) FROM events WHERE trip=trips.id AND kind IN (1,2) AND state='Confirmado'),(SELECT COUNT(*) FROM events WHERE trip=trips.id AND kind IN (1,2)) FROM trips ORDER BY id DESC LIMIT 80", null)) {
            while (c.moveToNext()) rows.add(new Trip(c));
        }
        return rows;
    }
    long sms(long event, String contact, int parts, String state) {
        ContentValues v = new ContentValues(); v.put("event_id", event); v.put("contact", contact); v.put("expected", parts); v.put("state", state);
        long id = getWritableDatabase().insertOrThrow("sms", null, v); revision.incrementAndGet(); return id;
    }
    void smsResult(long id, boolean success) {
        getWritableDatabase().execSQL("UPDATE sms SET done=done+1,failed=failed+?,state=CASE WHEN failed+?>0 THEN 'Falhou' WHEN done+1>=expected THEN 'Aceito pela operadora' ELSE 'Enviando' END WHERE id=? AND done<expected", new Object[]{success ? 0 : 1, success ? 0 : 1, id});
        revision.incrementAndGet();
    }
    String smsSummary(long event) {
        StringBuilder b = new StringBuilder();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT contact,state FROM sms WHERE event_id=? ORDER BY id", new String[]{"" + event})) {
            while (c.moveToNext()) b.append(c.getString(0)).append(": ").append(c.getString(1)).append('\n');
        }
        return b.toString().trim();
    }
    static final class Entry {
        final long id, event; final int type; final String body;
        Entry(long id, int type, String body, long event) { this.id = id; this.type = type; this.body = body; this.event = event; }
    }
    static final class Event {
        final long id, at, trip; final String title, detail, state, remoteId, payload; final int kind;
        Event(Cursor c) { id=c.getLong(0); at=c.getLong(1); trip=c.getLong(2); title=c.getString(3); detail=c.getString(4); kind=c.getInt(5); state=c.getString(6); remoteId=c.getString(7); payload=c.getString(8); }
    }
    static final class Trip {
        final long id, start, finish, last; final double distance; final int points, sent, total;
        Trip(Cursor c) { id=c.getLong(0); start=c.getLong(1); finish=c.getLong(2); last=c.getLong(3); distance=c.getDouble(4); points=c.getInt(5); sent=c.getInt(6); total=c.getInt(7); }
    }
}

