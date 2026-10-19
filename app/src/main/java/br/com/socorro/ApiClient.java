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
    static JSONObject parseResponse(int status, String text) throws IOException {
        if (status != 200) throw new IOException(status == 405 ? "Servidor recusou o envio POST (HTTP 405). O responsável pela API precisa verificar o endereço." : "Servidor respondeu HTTP " + status + ". Registro não confirmado.");
        try { return new JSONObject(text.replaceFirst("^\\uFEFF", "")); }
        catch (org.json.JSONException error) {
            String lower = text.toLowerCase(java.util.Locale.ROOT);
            throw new IOException(lower.contains("captcha") || lower.contains("verificação de segurança") || lower.contains("security check") ? "Servidor exige uma verificação de segurança e não retornou a resposta da API." : "Servidor retornou uma página em vez da resposta JSON da API.");
        }
    }
    static void validateConfirmation(JSONObject response, JSONObject request) throws Exception {
        if (!"sucesso".equals(response.optString("status")))
            throw new IOException("Servidor não confirmou a gravação.");
    }
    static String confirmationWarning(JSONObject response, JSONObject request) {
        if (response.has("usuario") && !request.optString("usuario").equals(response.optString("usuario")))
            return "A API informou sucesso, mas retornou um nome diferente. Confira o registro no painel.";
        if (response.has("tipo_registro") && request.optInt("tipo_registro") != response.optInt("tipo_registro"))
            return "A API informou sucesso, mas retornou outro tipo de registro. Confira o painel.";
        return "";
    }
    static String errorMessage(Exception error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof javax.net.ssl.SSLException) return "Não foi possível conectar por HTTPS. Falha de segurança na conexão com o servidor; nenhum envio foi confirmado.";
            if (cause instanceof java.net.UnknownHostException) return "Endereço da API não encontrado. Confira a conexão com a internet.";
            if (cause instanceof java.net.SocketTimeoutException) return "O servidor não respondeu no tempo esperado. Registro salvo para tentar novamente.";
            if (cause instanceof java.net.ConnectException) return "Não foi possível conectar ao servidor. Registro salvo para tentar novamente.";
        }
        return error instanceof IOException && error.getMessage() != null ? error.getMessage() : "Não foi possível concluir o envio. O registro continua salvo no aparelho.";
    }
    private static JSONObject http(String method, String url, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        try {
            c.setRequestMethod(method); c.setConnectTimeout(10000); c.setReadTimeout(10000);
            c.setInstanceFollowRedirects(false); c.setRequestProperty("Accept", "application/json");
            if (body != null) {
                c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (OutputStream out = c.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
            }
            int status = c.getResponseCode();
            InputStream stream = status >= 400 ? c.getErrorStream() : c.getInputStream();
            if (stream == null) throw new IOException("Servidor respondeu HTTP " + status + " sem conteúdo.");
            try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = in.read(buffer)) != -1) { out.write(buffer, 0, n); if (out.size() > 524288) throw new IOException("Resposta muito grande"); }
                return parseResponse(status, out.toString("UTF-8"));
            }
        } finally { c.disconnect(); }
    }
}
