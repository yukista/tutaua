package com.yukista.tutaua.manager;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

final class ControlClient {
    static JSONObject enroll(Context context, String server, String lanAddress, String code) throws Exception {
        JSONObject request = new JSONObject().put("code", code).put("publicKey", DeviceIdentity.publicKey())
                .put("inventory", Inventory.collect(context));
        return json("POST", cleanServer(server) + "/v1/device-enrollments", null, lanAddress, request);
    }
    static JSONObject checkIn(Context context, ManagerStore store) throws Exception {
        JSONObject reported = ReportedConfig.collect(context, store);
        JSONObject request = new JSONObject().put("configVersion", store.configVersion())
                .put("reportedConfig", reported).put("inventory", Inventory.collect(context));
        return json("POST", store.server() + "/v1/devices/check-in", store.token(), store.lanAddress(), request);
    }
    static void commandResult(ManagerStore store, String id, boolean success, JSONObject result) throws Exception {
        JSONObject request = new JSONObject().put("status", success ? "succeeded" : "failed").put("result", result);
        json("POST", store.server() + "/v1/devices/commands/" + id + "/result", store.token(), store.lanAddress(), request);
    }
    static File download(Context context, String url, String token, String lanAddress, String name) throws Exception {
        HttpURLConnection connection = open(url, token, lanAddress); connection.setRequestMethod("GET");
        if (connection.getResponseCode() != 200) throw new IllegalStateException("download HTTP " + connection.getResponseCode());
        File directory = new File(context.getFilesDir(), "updates");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("cannot create update directory");
        File destination = new File(directory, name.replaceAll("[^A-Za-z0-9._-]", "_") + ".apk.part");
        try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024]; int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.getFD().sync();
        } finally { connection.disconnect(); }
        return destination;
    }
    private static JSONObject json(String method, String url, String token, String lanAddress, JSONObject body) throws Exception {
        HttpURLConnection connection = open(url, token, lanAddress); connection.setRequestMethod(method); connection.setDoOutput(true);
        byte[] content = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Type", "application/json"); connection.setFixedLengthStreamingMode(content.length);
        connection.getOutputStream().write(content);
        int status = connection.getResponseCode();
        try (InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream()) {
            String response = new String(readAll(input), StandardCharsets.UTF_8);
            if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status + " " + response);
            return new JSONObject(response);
        } finally { connection.disconnect(); }
    }
    private static HttpURLConnection open(String rawUrl, String token, String lanAddress) throws Exception {
        URL url = new URL(rawUrl); if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IllegalArgumentException("HTTPS required");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        if (sameSubnet(lanAddress)) connection.setSSLSocketFactory(new MappedTlsSocketFactory(lanAddress));
        connection.setConnectTimeout(15_000); connection.setReadTimeout(60_000); connection.setUseCaches(false);
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
        return connection;
    }
    private static boolean sameSubnet(String address) {
        if (!address.matches("(?:\\d{1,3}\\.){3}\\d{1,3}")) return false;
        String prefix = address.substring(0, address.lastIndexOf('.') + 1);
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress local : Collections.list(network.getInetAddresses()))
                    if (local instanceof Inet4Address && !local.isLoopbackAddress() && local.getHostAddress().startsWith(prefix)) return true;
        } catch (Exception ignored) {}
        return false;
    }
    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read); return output.toByteArray();
    }
    private static String cleanServer(String value) {
        String trimmed = value.trim().replaceAll("/+$", "");
        if (!trimmed.startsWith("https://")) throw new IllegalArgumentException("HTTPS required"); return trimmed;
    }
    private static final class MappedTlsSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate = (SSLSocketFactory) SSLSocketFactory.getDefault();
        private final String address;
        MappedTlsSocketFactory(String address) { this.address = address; }
        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public Socket createSocket(String host, int port) throws java.io.IOException {
            Socket plain = new Socket(address, port); return delegate.createSocket(plain, host, port, true);
        }
        @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws java.io.IOException { return createSocket(host, port); }
        @Override public Socket createSocket(InetAddress host, int port) throws java.io.IOException { return delegate.createSocket(host, port); }
        @Override public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws java.io.IOException { return delegate.createSocket(host, port, local, localPort); }
        @Override public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws java.io.IOException {
            if (socket != null) socket.close();
            Socket local = new Socket(address, port);
            return delegate.createSocket(local, host, port, true);
        }
    }
    private ControlClient() {}
}
