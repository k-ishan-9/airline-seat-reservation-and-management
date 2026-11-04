package com.arms.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(10_000);

    @Test
    public void storesSaltedHashNotThePassword() {
        String hash = hasher.hash("secret123".toCharArray());
        assertTrue(hash.startsWith("pbkdf2_sha256$10000$"));
        assertFalse(hash.contains("secret123"));
    }

    @Test
    public void verifiesTheRightPasswordOnly() {
        String hash = hasher.hash("secret123".toCharArray());
        assertTrue(hasher.verify("secret123".toCharArray(), hash));
        assertFalse(hasher.verify("secret124".toCharArray(), hash));
    }

    @Test
    public void samePasswordGetsADifferentHashEachTime() {
        assertNotEquals(hasher.hash("secret123".toCharArray()), hasher.hash("secret123".toCharArray()));
    }

    @Test
    public void hashesFromAnOlderIterationCountStillVerify() {
        String oldHash = new PasswordHasher(2_000).hash("secret123".toCharArray());
        assertTrue(hasher.verify("secret123".toCharArray(), oldHash));
    }

    @Test
    public void rejectsGarbage() {
        assertFalse(hasher.verify("x".toCharArray(), "not-a-hash"));
        assertFalse(hasher.verify("x".toCharArray(), null));
    }
}
