package com.growthkids.api.common.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.springframework.stereotype.Component;

/**
 * 密码哈希（规范 §71：Argon2）。使用 BouncyCastle 纯 Java Argon2id 实现。
 *
 * <p>编码格式为 PHC 字符串：{@code $argon2id$v=19$m=65536,t=3,p=1$salt$hash}，
 * salt/hash 为无 padding 的 Base64。
 */
@Component
public class PasswordHasher {

    private static final int ARGON2_VERSION = Argon2Parameters.ARGON2_VERSION_13;
    private static final int DEFAULT_ITERATIONS = 3;
    private static final int DEFAULT_MEMORY_KB = 65536;
    private static final int DEFAULT_PARALLELISM = 1;
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final String PREFIX = "$argon2id$";

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getDecoder();

    /** 生成随机盐的 Argon2id 哈希。 */
    public String hash(String rawPassword) {
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        byte[] digest =
                argon2(
                        rawPassword.toCharArray(),
                        salt,
                        DEFAULT_MEMORY_KB,
                        DEFAULT_ITERATIONS,
                        DEFAULT_PARALLELISM,
                        HASH_LENGTH);
        return PREFIX
                + "v="
                + ARGON2_VERSION
                + "$m="
                + DEFAULT_MEMORY_KB
                + ",t="
                + DEFAULT_ITERATIONS
                + ",p="
                + DEFAULT_PARALLELISM
                + "$"
                + encoder.encodeToString(salt)
                + "$"
                + encoder.encodeToString(digest);
    }

    /** 常量时间比较校验密码。 */
    public boolean matches(String rawPassword, String encoded) {
        if (rawPassword == null || encoded == null || !encoded.startsWith(PREFIX)) {
            return false;
        }
        String[] parts = encoded.split("\\$");
        if (parts.length != 6) {
            return false;
        }
        try {
            int version = Integer.parseInt(parts[2].substring("v=".length()));
            if (version != ARGON2_VERSION) {
                return false;
            }
            int[] params = parseParams(parts[3]);
            byte[] salt = decoder.decode(parts[4]);
            byte[] expected = decoder.decode(parts[5]);
            byte[] actual =
                    argon2(
                            rawPassword.toCharArray(),
                            salt,
                            params[0],
                            params[1],
                            params[2],
                            expected.length);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private byte[] argon2(
            char[] password, byte[] salt, int memoryKb, int iterations, int parallelism, int hashLength) {
        Argon2Parameters params =
                new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                        .withVersion(ARGON2_VERSION)
                        .withSalt(salt)
                        .withMemoryAsKB(memoryKb)
                        .withIterations(iterations)
                        .withParallelism(parallelism)
                        .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        byte[] output = new byte[hashLength];
        generator.generateBytes(password, output);
        return output;
    }

    /** 解析 "m=65536,t=3,p=1" → {memory, iterations, parallelism}。 */
    private static int[] parseParams(String raw) {
        String[] items = raw.split(",");
        if (items.length != 3) {
            throw new IllegalArgumentException("Invalid Argon2 params: " + raw);
        }
        int memory = Integer.parseInt(items[0].substring("m=".length()));
        int iterations = Integer.parseInt(items[1].substring("t=".length()));
        int parallelism = Integer.parseInt(items[2].substring("p=".length()));
        return new int[] {memory, iterations, parallelism};
    }
}
