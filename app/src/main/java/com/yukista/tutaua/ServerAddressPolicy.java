package com.yukista.tutaua;

import java.net.URI;
import java.util.Locale;

/** Security policy for user-provided Jellyfin server addresses. */
final class ServerAddressPolicy {
    private ServerAddressPolicy() {}

    static boolean isSecureOrLocal(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) return false;
            if ("https".equalsIgnoreCase(scheme)) return true;
            if (!"http".equalsIgnoreCase(scheme)) return false;

            host = host.toLowerCase(Locale.ROOT);
            if ("localhost".equals(host) || host.endsWith(".local") || "::1".equals(host)) return true;
            String[] parts = host.split("\\.");
            if (parts.length != 4) return false;
            int[] octets = new int[4];
            for (int i = 0; i < parts.length; i++) {
                octets[i] = Integer.parseInt(parts[i]);
                if (octets[i] < 0 || octets[i] > 255) return false;
            }
            return octets[0] == 10 || octets[0] == 127
                    || (octets[0] == 192 && octets[1] == 168)
                    || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
