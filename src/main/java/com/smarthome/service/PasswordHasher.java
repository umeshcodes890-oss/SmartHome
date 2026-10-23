package com.smarthome.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

public final class PasswordHasher {
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int ITERATIONS = 3;
    private static final int MEMORY_KIB = 65_536;
    private static final int PARALLELISM = 1;
    private final SecureRandom random = new SecureRandom();

    public String hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, ITERATIONS, MEMORY_KIB, PARALLELISM, HASH_BYTES);
        return "$argon2id$v=19$m=" + MEMORY_KIB + ",t=" + ITERATIONS + ",p=" + PARALLELISM + "$"
            + Base64.getEncoder().withoutPadding().encodeToString(salt) + "$"
            + Base64.getEncoder().withoutPadding().encodeToString(hash);
    }

    public boolean verify(char[] password, String encoded) {
        if (password == null || encoded == null) return false;
        try {
            String[] parts = encoded.split("\\$");
            if (parts.length != 6 || !"argon2id".equals(parts[1]) || !"v=19".equals(parts[2])) return false;
            String[] parameters = parts[3].split(",");
            int memory = Integer.parseInt(parameters[0].substring(2));
            int iterations = Integer.parseInt(parameters[1].substring(2));
            int parallelism = Integer.parseInt(parameters[2].substring(2));
            if (memory < 8 * parallelism || memory > 262_144 || iterations < 1 || iterations > 10
                    || parallelism < 1 || parallelism > 4) return false;
            byte[] salt = Base64.getDecoder().decode(parts[4]);
            byte[] expected = Base64.getDecoder().decode(parts[5]);
            if (salt.length < 8 || salt.length > 64 || expected.length < 16 || expected.length > 64) return false;
            byte[] actual = derive(password, salt, iterations, memory, parallelism, expected.length);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private byte[] derive(char[] password, byte[] salt, int iterations, int memory, int parallelism, int length) {
        Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(iterations)
            .withMemoryAsKB(memory)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(parameters);
        byte[] output = new byte[length];
        generator.generateBytes(password, output);
        return output;
    }
}
