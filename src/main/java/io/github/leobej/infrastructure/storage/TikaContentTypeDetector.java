package io.github.leobej.infrastructure.storage;

import io.github.leobej.domain.port.ContentTypeDetector;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

// Magic bytes only - tika-core, no parsers. The file name is deliberately not passed: it would let
// Tika fall back on the extension, which is just as easy to lie about as the Content-Type header.
@Component
public class TikaContentTypeDetector implements ContentTypeDetector {

    private final Tika tika = new Tika();

    @Override
    public String detect(byte[] content) {
        return tika.detect(content);
    }
}
