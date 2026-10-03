package com.banking.api.util;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class ApiKeyGenerator {

    @Value("${api.key.length:32}")
    private int apiKeyLength;

    @Value("${api.key.secret-length:64}")
    private int apiSecretLength;

    public String generateApiKey() {
        // Format: api_ + timestamp + random
        String prefix = "api_";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String random = RandomStringUtils.randomAlphanumeric(apiKeyLength - prefix.length() - timestamp.length());
        return prefix + timestamp + random;
    }

    public String generateApiSecret() {
        return "sec_" + RandomStringUtils.randomAlphanumeric(apiSecretLength);
    }

    public String hashSecret(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public boolean verifySecret(String rawSecret, String hashedSecret) {
        String computedHash = hashSecret(rawSecret);
        return computedHash.equals(hashedSecret);
    }
}
