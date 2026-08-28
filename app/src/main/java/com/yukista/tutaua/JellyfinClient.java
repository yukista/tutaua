package com.yukista.tutaua;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class JellyfinClient {
    private JellyfinClient() {}

    static JSONObject request(String url, String method, String body, String token, String authorization) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method); connection.setConnectTimeout(12000); connection.setReadTimeout(25000);
        connection.setRequestProperty("Accept", "application/json"); connection.setRequestProperty("Content-Type", "application/json"); connection.setRequestProperty("Authorization", authorization);
        if (token != null) connection.setRequestProperty("X-Emby-Token", token);
        if (body != null) { connection.setDoOutput(true); try (OutputStream out = connection.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); } }
        int code = connection.getResponseCode(); InputStream raw = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream(); StringBuilder result = new StringBuilder();
        if (raw != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8))) { String line; while ((line = reader.readLine()) != null) result.append(line); }
        connection.disconnect(); if (code < 200 || code >= 300) throw new Exception("HTTP " + code); return new JSONObject(result.length() == 0 ? "{}" : result.toString());
    }
}
