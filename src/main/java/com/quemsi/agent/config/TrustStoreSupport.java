package com.quemsi.agent.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import javax.net.ssl.TrustManagerFactory;

import org.springframework.util.StringUtils;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

public final class TrustStoreSupport {

    private TrustStoreSupport() {
    }

    static SslContext nettyClientSslContext(String path, String password, String type) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        Path truststorePath = Path.of(path);
        if (!Files.isRegularFile(truststorePath)) {
            throw new IllegalStateException("Truststore file not found or not readable: " + path);
        }
        String storeType = StringUtils.hasText(type) ? type : KeyStore.getDefaultType();
        char[] storePassword = StringUtils.hasText(password) ? password.toCharArray() : null;
        try {
            KeyStore trustStore = KeyStore.getInstance(storeType);
            try (InputStream inputStream = Files.newInputStream(truststorePath)) {
                trustStore.load(inputStream, storePassword);
            }
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            return SslContextBuilder.forClient()
                    .trustManager(trustManagerFactory)
                    .build();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load truststore from " + path, ex);
        }
    }
}
