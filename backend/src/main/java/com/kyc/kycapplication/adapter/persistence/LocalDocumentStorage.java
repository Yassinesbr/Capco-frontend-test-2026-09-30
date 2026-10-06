package com.kyc.kycapplication.adapter.persistence;

import com.kyc.kycapplication.application.port.out.DocumentStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Private filesystem document store with generated keys and bounded streaming writes. */
@Component
public class LocalDocumentStorage implements DocumentStorage {

    private final Path root;

    /** Creates the store under a non-public configurable directory. */
    public LocalDocumentStorage(@Value("${kyc.document-storage.root:./var/kyc-documents}") final String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public StoredDocument store(final InputStream content, final String mediaType, final long size) throws IOException {
        Files.createDirectories(root);
        String extension = "image/png".equalsIgnoreCase(mediaType) ? ".png" : ".jpg";
        Path target = root.resolve(UUID.randomUUID() + extension).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("invalid storage target");
        }
        MessageDigest digest = digest();
        long copied = 0;
        try (InputStream input = content; var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                copied += read;
                if (copied > size || copied > 10L * 1024L * 1024L) {
                    throw new IOException("document exceeds declared size");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        } catch (IOException failure) {
            Files.deleteIfExists(target);
            throw failure;
        }
        if (copied != size) {
            Files.deleteIfExists(target);
            throw new IOException("document size does not match request");
        }
        return new StoredDocument(root.relativize(target).toString(), mediaType, copied,
                HexFormat.of().formatHex(digest.digest()));
    }

    @Override
    public void delete(final String storageKey) throws IOException {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("invalid storage target");
        }
        Files.deleteIfExists(target);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
