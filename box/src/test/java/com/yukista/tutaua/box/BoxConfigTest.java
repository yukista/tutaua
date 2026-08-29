package com.yukista.tutaua.box;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BoxConfigTest {
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

    @Test public void learnedRemoteKeyCodesStayWithinAndroidRange() {
        assertTrue(BoxConfig.validKeyCode(0));
        assertTrue(BoxConfig.validKeyCode(132));
        assertTrue(BoxConfig.validKeyCode(400));
        assertFalse(BoxConfig.validKeyCode(-1));
        assertFalse(BoxConfig.validKeyCode(401));
    }
}
