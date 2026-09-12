package com.yukista.tutaua.manager;

import java.net.URI;

final class ArtifactUrls {
    // Call only after verifying the original signed manifest. The selected Fleet
    // serves the same artifact ID; hash, APK signature and version are still checked.
    static String atServer(String signedUrl, String server) {
        URI source = URI.create(signedUrl);
        URI target = URI.create(server);
        String path = source.getPath();
        int marker = path == null ? -1 : path.lastIndexOf("/v1/artifacts/");
        if (!"https".equalsIgnoreCase(target.getScheme()) || target.getHost() == null
                || target.getUserInfo() != null || target.getQuery() != null || target.getFragment() != null
                || marker < 0 || source.getQuery() != null || source.getFragment() != null)
            throw new IllegalArgumentException("invalid Fleet artifact URL");
        String id = path.substring(marker + "/v1/artifacts/".length());
        if (!id.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("invalid artifact ID");
        return server.replaceAll("/+$", "") + "/v1/artifacts/" + id;
    }
    private ArtifactUrls() { }
}
