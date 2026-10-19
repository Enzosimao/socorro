package br.com.socorro;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

final class ApiClient {
    static final String ENDPOINT = "https://ideaberta.com.br/socorro/";
    interface Transport { JSONObject request(String method, String url, String body) throws Exception; }
    
    static volatile Transport transport = ApiClient::http;
    static JSONObject post(String body) throws Exception { return transport.request("POST", ENDPOINT, body); }
    static JSONObject get(String url) throws Exception { return transport.request("GET", url, null); }
    private static JSONObject http(String method, String url, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        try {
            c.setRequestMethod(method); c.setConnectTimeout(10000); c.setReadTimeout(10000);
            c.setInstanceFollowRedirects(false); c.setRequestProperty("Accept", "application/json");
            if (body != null) {
                c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (OutputStream out = c.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
            }
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = in.read(buffer)) != -1) { out.write(buffer, 0, n); if (out.size() > 524288) throw new IOException("Resposta muito grande"); }
                return new JSONObject(out.toString("UTF-8"));
            }
        } finally { c.disconnect(); }
    }
}
