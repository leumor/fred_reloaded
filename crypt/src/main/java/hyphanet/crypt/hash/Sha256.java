/**
 * Cryptix General Licence Copyright (C) 1995, 1996, 1997, 1998, 1999, 2000 The Cryptix
 * Foundation Limited. All rights reserved. Redistribution and use in source and binary forms,
 * with or without modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the copyright notice, this list of conditions
 * and the following disclaimer. 2. Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution. THIS SOFTWARE IS PROVIDED BY THE
 * CRYPTIX FOUNDATION LIMITED ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * <p>
 * Copyright (C) 2000 The Cryptix Foundation Limited. All rights reserved.
 * <p>
 * Use, modification, copying and distribution of this software is subject to the terms and
 * conditions of the Cryptix General Licence. You should have received a copy of the Cryptix
 * General Licence along with this library; if not, you can download a copy from
 * http://www.cryptix.org/ .
 */
package hyphanet.crypt.hash;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Utility class for SHA-256 hashing operations with digest pooling. This implementation is
 * thread-safe and memory-efficient.
 *
 * @author Jeroen C. van Gelderen (gelderen@cryptix.org)
 */
public final class Sha256 {
    /**
     * Size (in bytes) of this hash
     */
    private static final int HASH_SIZE = 32;
    private static final Queue<SoftReference<MessageDigest>> digests =
        new ConcurrentLinkedQueue<>();

    private Sha256() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Computes hash from input stream using provided MessageDigest. The digest will not be
     * reset automatically.
     *
     * @param is Input stream to hash
     * @param md MessageDigest instance to use
     *
     * @throws IOException if an I/O error occurs
     */
    public static void hash(InputStream is, MessageDigest md) throws IOException {
        try (InputStream input = is) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * Creates or retrieves a pooled SHA-256 MessageDigest instance.
     *
     * @return A SHA-256 MessageDigest instance
     *
     * @throws IllegalStateException if SHA-256 algorithm is not available
     */
    public static MessageDigest getMessageDigest() {
        SoftReference<MessageDigest> ref;
        while (((ref = digests.poll()) != null)) {
            MessageDigest md = ref.get();
            if (md != null) {
                return md;
            }
        }

        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Returns an SHA-256 MessageDigest instance to the pool.
     *
     * @param md256 The MessageDigest to return
     *
     * @throws IllegalArgumentException if the algorithm is not SHA-256
     */
    public static void returnMessageDigest(MessageDigest md256) {
        if (md256 == null) {
            return;
        }
        String algo = md256.getAlgorithm();
        if (!("SHA-256".equals(algo) || "SHA256".equals(algo))) {
            throw new IllegalArgumentException("Expected SHA-256 algorithm but got: " + algo);
        }
        md256.reset();
        digests.add(new SoftReference<>(md256));
    }


    /**
     * Computes SHA-256 hash of the input data.
     *
     * @param data The data to hash
     *
     * @return The computed hash
     */
    public static byte[] digest(byte[] data) {
        MessageDigest md = getMessageDigest();
        try {
            return md.digest(data);
        } finally {
            returnMessageDigest(md);
        }
    }

    /**
     * Returns the length of SHA-256 digest in bytes.
     *
     * @return The digest length (32 bytes)
     */
    public static int getDigestLength() {
        return HASH_SIZE;
    }
}
