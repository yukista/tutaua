package com.yukista.tutaua.manager;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class UpdateInstaller {
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(Inventory.PACKAGES));

    static String install(Context context, ManagerStore store, JSONObject release) throws Exception {
        verifyManifest(release);
        String packageName = release.getString("applicationId");
        if (!ALLOWED.contains(packageName)) throw new SecurityException("package is not allowed");
        File apk = ControlClient.download(context, release.getString("url"), store.token(), store.lanAddress(), packageName);
        try {
            String expectedHash = release.getString("sha256").toLowerCase(Locale.ROOT);
            if (!constantTime(expectedHash, sha256(apk))) throw new SecurityException("APK hash mismatch");
            PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
            if (archive == null || !packageName.equals(archive.packageName)) throw new SecurityException("APK package mismatch");
            String archiveCertificate = certificate(archive);
            String catalogCertificate = release.getString("certificateSha256").toLowerCase(Locale.ROOT);
            if (!constantTime(catalogCertificate, archiveCertificate)) throw new SecurityException("catalog certificate mismatch");
            PackageInfo installed = context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            if (!constantTime(certificate(installed), archiveCertificate)) throw new SecurityException("signing certificate changed");
            if (archive.getLongVersionCode() != release.getLong("versionCode")) throw new SecurityException("version mismatch");
            Process process = new ProcessBuilder("su", "-c", "pm install -r --user 0 " + shellQuote(apk.getAbsolutePath()))
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            if (process.waitFor() != 0 || !output.contains("Success")) throw new IllegalStateException("install failed");
            return output.trim();
        } finally { if (!apk.delete()) apk.deleteOnExit(); }
    }

    private static String certificate(PackageInfo info) throws Exception {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28
                ? (info.signingInfo.hasMultipleSigners() ? info.signingInfo.getApkContentsSigners() : info.signingInfo.getSigningCertificateHistory())
                : info.signatures;
        if (signatures == null || signatures.length == 0) throw new SecurityException("missing signing certificate");
        return hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()));
    }
    private static void verifyManifest(JSONObject release) throws Exception {
        String payload = release.getString("applicationId") + "\n" + release.getLong("versionCode") + "\n"
                + release.getString("versionName") + "\n" + release.getString("url") + "\n"
                + release.getLong("size") + "\n" + release.getString("sha256") + "\n"
                + release.getString("certificateSha256") + "\n" + (release.getBoolean("critical") ? "true" : "false");
        net.i2p.crypto.eddsa.spec.EdDSAParameterSpec parameters =
                net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable.getByName("Ed25519");
        net.i2p.crypto.eddsa.EdDSAPublicKey key = new net.i2p.crypto.eddsa.EdDSAPublicKey(
                new net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec(
                        android.util.Base64.decode(BuildConfig.RELEASE_MANIFEST_PUBLIC_KEY, android.util.Base64.NO_WRAP), parameters));
        net.i2p.crypto.eddsa.EdDSAEngine verifier = new net.i2p.crypto.eddsa.EdDSAEngine(
                java.security.MessageDigest.getInstance("SHA-512"));
        verifier.initVerify(key);
        verifier.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!verifier.verify(android.util.Base64.decode(release.getString("manifestSignature"), android.util.Base64.NO_WRAP)))
            throw new SecurityException("release manifest signature mismatch");
    }
    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024]; int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }
    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(); for (byte item : bytes) value.append(String.format(Locale.ROOT, "%02x", item)); return value.toString();
    }
    private static boolean constantTime(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(java.nio.charset.StandardCharsets.US_ASCII), second.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
    private static String shellQuote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
    private UpdateInstaller() {}
}
