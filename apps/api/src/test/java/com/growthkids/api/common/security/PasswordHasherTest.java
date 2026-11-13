package com.growthkids.api.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** PasswordHasher 单元测试（规范 §71：Argon2）。 */
class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashUsesArgon2idPhcFormat() {
        String encoded = hasher.hash("StrongPassword123!");
        assertTrue(encoded.startsWith("$argon2id$v=19$m=65536,t=3,p=1$"));
        assertEquals(6, encoded.split("\\$").length);
    }

    @Test
    void matchesReturnsTrueForCorrectPassword() {
        String encoded = hasher.hash("StrongPassword123!");
        assertTrue(hasher.matches("StrongPassword123!", encoded));
    }

    @Test
    void matchesReturnsFalseForWrongPassword() {
        String encoded = hasher.hash("StrongPassword123!");
        assertFalse(hasher.matches("wrong-password", encoded));
    }

    @Test
    void hashesUseUniqueSalts() {
        String first = hasher.hash("same-password");
        String second = hasher.hash("same-password");
        assertNotEquals(first, second);
        assertTrue(hasher.matches("same-password", first));
        assertTrue(hasher.matches("same-password", second));
    }

    @Test
    void matchesReturnsFalseForInvalidInput() {
        assertFalse(hasher.matches("pw", "not-a-hash"));
        assertFalse(hasher.matches("pw", null));
        assertFalse(hasher.matches(null, hasher.hash("pw")));
        assertFalse(hasher.matches("pw", "$argon2id$v=19$m=65536,t=3,p=1$c2FsdA$aGFzaA"));
    }
}
