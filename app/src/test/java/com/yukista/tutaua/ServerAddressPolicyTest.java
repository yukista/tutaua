package com.yukista.tutaua;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServerAddressPolicyTest {
    @Test public void acceptsHttpsPublicServers() {
        assertTrue(ServerAddressPolicy.isSecureOrLocal("https://demo.example.org"));
        assertTrue(ServerAddressPolicy.isSecureOrLocal("https://tutaua.duckdns.org:8920"));
    }

    @Test public void acceptsLocalHttpServers() {
        assertTrue(ServerAddressPolicy.isSecureOrLocal("http://192.168.1.139:8096"));
        assertTrue(ServerAddressPolicy.isSecureOrLocal("http://10.0.0.4"));
        assertTrue(ServerAddressPolicy.isSecureOrLocal("http://172.31.2.8:8096"));
        assertTrue(ServerAddressPolicy.isSecureOrLocal("http://jellyfin.local:8096"));
        assertTrue(ServerAddressPolicy.isSecureOrLocal("http://localhost:8096"));
    }

    @Test public void rejectsPublicCleartextAndMalformedAddresses() {
        assertFalse(ServerAddressPolicy.isSecureOrLocal("http://example.org"));
        assertFalse(ServerAddressPolicy.isSecureOrLocal("http://8.8.8.8:8096"));
        assertFalse(ServerAddressPolicy.isSecureOrLocal("192.168.1.10:8096"));
        assertFalse(ServerAddressPolicy.isSecureOrLocal("ftp://192.168.1.10"));
        assertFalse(ServerAddressPolicy.isSecureOrLocal("not a url"));
    }
}
