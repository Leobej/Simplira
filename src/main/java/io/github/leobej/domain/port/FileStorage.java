package io.github.leobej.domain.port;

import java.time.Duration;

// §27's StorageService, named for what it holds. A port so the domain decides what goes where
// without knowing it is S3 - or R2, or S3Mock on a laptop.
public interface FileStorage {

    void store(String key, byte[] content, String contentType);

    // A time-limited link straight to the bytes, so they never pass through this server on the way
    // out. downloadName is what a browser offers when someone saves the file.
    String temporaryUrl(String key, Duration validFor, String downloadName);

    void delete(String key);
}
