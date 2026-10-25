package io.github.leobej.infrastructure.storage;

import io.github.leobej.domain.port.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class S3FileStorage implements FileStorage {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3FileStorage(S3Client s3Client, S3Presigner presigner, @Value("${simplira.storage.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public void store(String key, byte[] content, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
    }

    // inline, so an image opens in the browser, with the original name - RFC 5987 encoded, since
    // it is whatever the uploader called it - for when someone saves it.
    @Override
    public String temporaryUrl(String key, Duration validFor, String downloadName) {
        String encodedName = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .responseContentDisposition("inline; filename*=UTF-8''" + encodedName)
                .build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(validFor)
                        .getObjectRequest(get)
                        .build())
                .url()
                .toString();
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(request -> request.bucket(bucket).key(key));
    }
}
