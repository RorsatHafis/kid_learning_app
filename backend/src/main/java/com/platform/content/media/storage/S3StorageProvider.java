package com.platform.content.media.storage;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * M11: a school's own S3-compatible bucket (AWS S3, MinIO, Wasabi, ...). Objects are kept under
 * {@code bookeyond/<media key>}; a write is read back and its SHA-256 checked before it counts, as on disk.
 */
public class S3StorageProvider implements StorageProvider, AutoCloseable {

    static final String PREFIX = "bookeyond/";

    private final S3Client client;
    private final String bucket;

    public S3StorageProvider(URI endpoint, String region, String bucket, boolean pathStyle, String accessKeyId, String accessKey) {
        this.bucket = bucket;
        this.client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(region))
                .forcePathStyle(pathStyle)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, accessKey)))
                .httpClient(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(20)).build())
                .build();
    }

    /** A stored connection, opened - its address checked again first (DNS may have changed since it was connected). */
    public static S3StorageProvider of(StorageConnection connection, String accessKey, boolean allowPrivate) {
        URI endpoint = EndpointPolicy.check(connection.getEndpoint(), allowPrivate);
        return new S3StorageProvider(endpoint, connection.getRegion(), connection.getBucket(), connection.isPathStyle(),
                connection.getAccessKeyId(), accessKey);
    }

    @Override
    public void put(String key, String sha256, byte[] bytes) {
        if (intact(key, sha256, bytes.length)) {
            return;
        }
        try {
            client.putObject(request -> request.bucket(bucket).key(PREFIX + key).contentLength((long) bytes.length),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            throw new UncheckedIOException(new IOException("The school's storage refused the file (" + e.statusCode() + ")"));
        }
        if (!intact(key, sha256, bytes.length)) {
            throw new UncheckedIOException(new IOException("The file did not read back from the school's storage as written"));
        }
    }

    @Override
    public boolean intact(String key, String sha256, long size) {
        try {
            byte[] bytes = read(key);
            return bytes.length == size && sha256(bytes).equals(sha256);
        } catch (UncheckedIOException e) {
            return false;
        }
    }

    @Override
    public byte[] read(String key) {
        try {
            return client.getObjectAsBytes(request -> request.bucket(bucket).key(PREFIX + key)).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new UncheckedIOException(new IOException("Not in the school's storage: " + key));
        } catch (S3Exception e) {
            throw new UncheckedIOException(new IOException("The school's storage could not be read (" + e.statusCode() + ")"));
        } catch (RuntimeException e) {
            throw new UncheckedIOException(new IOException("The school's storage could not be reached"));
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(request -> request.bucket(bucket).key(PREFIX + key));
        } catch (RuntimeException e) {
            throw new UncheckedIOException(new IOException("Could not delete from the school's storage"));
        }
    }

    /**
     * A connection check: write a small marker, read it back, delete it. Empty when all three worked; otherwise what
     * failed, in words a principal can act on (never the provider's raw message, which may echo request details).
     */
    public java.util.Optional<String> check() {
        String marker = "check/" + java.util.UUID.randomUUID();
        byte[] bytes = ("Bookeyond storage check " + java.time.Instant.now()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            client.putObject(request -> request.bucket(bucket).key(PREFIX + marker), RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            return java.util.Optional.of(e.statusCode() == 403 ? "The storage refused the access key, or it can't write to that bucket."
                    : e.statusCode() == 404 ? "That bucket doesn't exist at this address." : "The storage refused a test file.");
        } catch (RuntimeException e) {
            return java.util.Optional.of("The storage address couldn't be reached.");
        }
        try {
            if (!java.util.Arrays.equals(read(marker), bytes)) {
                return java.util.Optional.of("A test file didn't read back as written.");
            }
        } catch (UncheckedIOException e) {
            return java.util.Optional.of("A test file couldn't be read back.");
        }
        try {
            delete(marker);
        } catch (UncheckedIOException e) {
            return java.util.Optional.of("A test file couldn't be deleted: the key needs permission to delete.");
        }
        return java.util.Optional.empty();
    }

    @Override
    public void close() {
        client.close();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
