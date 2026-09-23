package com.yukista.tutaua.box;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BoxConfigTest {
    @Test public void fleetPreservesTlsAndAllowsExplicitPublicOnlyRouting() {
        assertTrue(BoxConfig.validFleetUrl("https://fleet.example/control"));
        assertTrue(BoxConfig.validFleetUrl(""));
        assertFalse(BoxConfig.validFleetUrl("http://192.168.1.139:8091"));
        assertFalse(BoxConfig.validFleetUrl("https://user:pass@fleet.example"));
        assertFalse(BoxConfig.validFleetUrl("https://fleet.example/control?token=abc"));
        assertTrue(BoxConfig.validLanAddress(""));
        assertTrue(BoxConfig.validLanAddress("192.168.1.139"));
        assertFalse(BoxConfig.validLanAddress("192.168.1.999"));
        assertFalse(BoxConfig.validLanAddress("8.8.8.8"));
    }

    @Test public void requiredServiceUrlsMustBeHttpOrHttps() {
        assertTrue(BoxConfig.validUrl("https://jellyfin.example", false));
        assertTrue(BoxConfig.validUrl("http://192.168.1.20:8096", false));
        assertFalse(BoxConfig.validUrl("", false));
        assertFalse(BoxConfig.validUrl("jellyfin.example", false));
        assertFalse(BoxConfig.validUrl("ftp://jellyfin.example", false));
    }

    @Test public void optionalUrlsMayBeEmpty() {
        assertTrue(BoxConfig.validUrl("", true));
        assertTrue(BoxConfig.validUrl(" https://updates.example/manifest.json ", true));
    }

    @Test public void updateChannelHasAConservativeFormat() {
        assertTrue(BoxConfig.validChannel("stable"));
        assertTrue(BoxConfig.validChannel("beta-1.2"));
        assertFalse(BoxConfig.validChannel(""));
        assertFalse(BoxConfig.validChannel("beta channel"));
    }

    @Test public void screenTimeoutStaysWithinSupportedRange() {
        assertTrue(BoxConfig.validTimeoutMinutes(1));
        assertTrue(BoxConfig.validTimeoutMinutes(120));
        assertFalse(BoxConfig.validTimeoutMinutes(0));
        assertFalse(BoxConfig.validTimeoutMinutes(121));
    }

    @Test public void jellyfinUsernameAndPasswordStayWithinBounds() {
        assertTrue(BoxConfig.validUsername(""));
        assertTrue(BoxConfig.validUsername(" veure "));
        assertFalse(BoxConfig.validUsername("line\nbreak"));
        assertFalse(BoxConfig.validUsername("x".repeat(65)));
        assertTrue(BoxConfig.validPassword(null));
        assertTrue(BoxConfig.validPassword(""));
        assertFalse(BoxConfig.validPassword("x".repeat(257)));
    }

    @Test public void learnedRemoteKeyCodesStayWithinAndroidRange() {
        assertTrue(BoxConfig.validKeyCode(0));
        assertTrue(BoxConfig.validKeyCode(132));
        assertTrue(BoxConfig.validKeyCode(400));
        assertFalse(BoxConfig.validKeyCode(-1));
        assertFalse(BoxConfig.validKeyCode(401));
    }
}
