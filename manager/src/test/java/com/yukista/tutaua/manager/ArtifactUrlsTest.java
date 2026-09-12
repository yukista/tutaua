package com.yukista.tutaua.manager;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ArtifactUrlsTest {
    @Test public void usesSelectedFleetIncludingProxyPrefix() {
        assertEquals("https://new.example/control/v1/artifacts/abc-123",
                ArtifactUrls.atServer("https://old.example/control/v1/artifacts/abc-123", "https://new.example/control/"));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsOtherDownloadRoutes() {
        ArtifactUrls.atServer("https://old.example/other/file.apk", "https://new.example/control");
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsCleartextCredentials() {
        ArtifactUrls.atServer("https://old.example/v1/artifacts/abc", "http://192.168.1.139:8091");
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsEncodedTraversal() {
        ArtifactUrls.atServer("https://old.example/v1/artifacts/%2e%2e", "https://new.example/control");
    }
}
