package br.com.socorro;

import android.content.SharedPreferences;
import org.json.*;

final class Contacts {
    static JSONArray read(SharedPreferences p) {
        try { return new JSONArray(p.getString("contacts", "[]")); } catch (JSONException e) { return new JSONArray(); }
    }
    static String phone(String input) { return input.trim().replaceAll("[\\s().-]", ""); }
    static boolean validPhone(String phone) { return phone.matches("\\+?[0-9]{10,15}"); }
    static boolean duplicate(JSONArray contacts, String phone) {
        String canonical = phone.replaceFirst("^\\+55(?=[0-9]{10,11}$)", "");
        for (int i = 0; i < contacts.length(); i++) {
            JSONObject contact = contacts.optJSONObject(i);
            if (contact != null && canonical.equals(contact.optString("phone").replaceFirst("^\\+55(?=[0-9]{10,11}$)", ""))) return true;
        }
        return false;
    }
}
