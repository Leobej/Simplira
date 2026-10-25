package io.github.leobej.infrastructure.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

// One configuration for every S3-compatible store (§2): AWS S3 with no endpoint set, Cloudflare
// R2 or S3Mock with one. Nothing connects at startup, so an app that never touches attachments
// runs without a store.
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    private final String region;
    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final boolean pathStyle;

    public StorageConfig(@Value("${simplira.storage.region}") String region,
                         @Value("${simplira.storage.endpoint}") String endpoint,
                         @Value("${simplira.storage.access-key}") String accessKey,
                         @Value("${simplira.storage.secret-key}") String secretKey,
                         @Value("${simplira.storage.path-style}") boolean pathStyle) {
        this.region = region;
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.pathStyle = pathStyle;
    }

    @Bean(destroyMethod = "close")
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build())
                // The SDK's default checksums on every request are newer than several
                // S3-compatible stores (R2 among them); AWS itself accepts either way.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build());
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    // Development and tests only. In production the bucket is provisioned with its own policy,
    // and the app's credentials should not be able to create buckets at all.
    @Bean
    @ConditionalOnProperty(name = "simplira.storage.create-bucket-if-missing", havingValue = "true")
    public ApplicationRunner createBucketIfMissing(S3Client s3Client,
                                                   @Value("${simplira.storage.bucket}") String bucket) {
        return args -> {
            try {
                s3Client.headBucket(request -> request.bucket(bucket));
            } catch (NoSuchBucketException ex) {
                s3Client.createBucket(request -> request.bucket(bucket));
                log.info("Created storage bucket: {}", bucket);
            }
        };
    }

    // Explicit keys when configured; otherwise the SDK's usual chain (environment, profile, the
    // instance role), which is what production on AWS should use.
    private AwsCredentialsProvider credentials() {
        if (accessKey.isBlank()) {
            return DefaultCredentialsProvider.builder().build();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
}
