package io.github.leobej.domain.port;

// §25: a file's type is read from its bytes. The Content-Type header and the extension are both
// whatever the client says they are.
public interface ContentTypeDetector {
    String detect(byte[] content);
}
