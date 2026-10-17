/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper.protocol;

import com.google.common.io.Resources;
import com.google.common.primitives.Longs;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import xyz.kyngs.librelogin.paper.PaperBootstrap;

/**
 * Cryptographic utility providing RSA key generation, AES secret decryption, Mojang session hash
 * calculation, and nonce verification.
 */
public final class EncryptionUtil {

    public static final int VERIFY_TOKEN_LENGTH = 4;
    public static final String KEY_PAIR_ALGORITHM = "RSA";

    private static final int RSA_LENGTH = 1_024;
    private static final PublicKey MOJANG_SESSION_KEY;
    private static final int LINE_LENGTH = 76;
    private static final Base64.Encoder KEY_ENCODER =
            Base64.getMimeEncoder(LINE_LENGTH, "\n".getBytes(StandardCharsets.UTF_8));
    private static final int MILLISECOND_SIZE = 8;
    private static final int UUID_SIZE = 2 * MILLISECOND_SIZE;

    static {
        try {
            MOJANG_SESSION_KEY = loadMojangSessionKey();
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new RuntimeException("Failed to load Mojang session key", ex);
        }
    }

    private EncryptionUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Generates a 1024-bit RSA key pair for login encryption handshake.
     *
     * @return the generated {@link KeyPair}
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_PAIR_ALGORITHM);
            keyPairGenerator.initialize(RSA_LENGTH);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException nosuchalgorithmexception) {
            throw new ExceptionInInitializerError(nosuchalgorithmexception);
        }
    }

    /**
     * Generates a random 4-byte verification token for connection authentication.
     *
     * @param random the random number generator
     * @return a 4-byte verification token
     */
    public static byte[] generateVerifyToken(Random random) {
        byte[] token = new byte[VERIFY_TOKEN_LENGTH];
        random.nextBytes(token);
        return token;
    }

    /**
     * Generates the Minecraft server ID hash string based on server ID, shared secret, and public
     * key.
     *
     * @param serverId the session server ID string
     * @param sharedSecret the shared AES secret key
     * @param publicKey the server RSA public key
     * @return the server ID hash formatted as a hexadecimal string
     */
    public static String getServerIdHashString(
            String serverId, SecretKey sharedSecret, PublicKey publicKey) {
        byte[] serverHash = getServerIdHash(serverId, publicKey, sharedSecret);
        return (new BigInteger(serverHash)).toString(16);
    }

    /**
     * Decrypts an encrypted shared secret using the server's private RSA key.
     *
     * @param privateKey the server private key
     * @param sharedKey the encrypted shared secret payload
     * @return the decrypted {@link SecretKey}
     * @throws NoSuchPaddingException if the padding scheme is unavailable
     * @throws IllegalBlockSizeException if block size is invalid
     * @throws NoSuchAlgorithmException if the algorithm is unavailable
     * @throws BadPaddingException if padding decryption fails
     * @throws InvalidKeyException if the private key is invalid
     */
    public static SecretKey decryptSharedKey(PrivateKey privateKey, byte[] sharedKey)
            throws NoSuchPaddingException,
                    IllegalBlockSizeException,
                    NoSuchAlgorithmException,
                    BadPaddingException,
                    InvalidKeyException {
        return new SecretKeySpec(decrypt(privateKey, sharedKey), "AES");
    }

    /**
     * Validates a client public key against the Mojang Yggdrasil session authority.
     *
     * @param clientKey the client public key data
     * @param verifyTimestamp the instant to check expiration against
     * @param premiumId the UUID of the premium account
     * @return {@code true} if signature is verified and not expired; {@code false} otherwise
     * @throws NoSuchAlgorithmException if the verification algorithm is missing
     * @throws InvalidKeyException if the signing key is invalid
     * @throws SignatureException if signature verification processing fails
     */
    public static boolean verifyClientKey(
            ClientPublicKey clientKey, Instant verifyTimestamp, UUID premiumId)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        if (clientKey.expired(verifyTimestamp)) {
            return false;
        }

        Signature verifier = Signature.getInstance("SHA1withRSA");
        verifier.initVerify(MOJANG_SESSION_KEY);
        verifier.update(toSignable(clientKey, premiumId));
        return verifier.verify(clientKey.signature());
    }

    private static byte[] toSignable(ClientPublicKey clientPublicKey, UUID ownerPremiumId) {
        if (ownerPremiumId == null) {
            long expiry = clientPublicKey.expire().toEpochMilli();
            String encoded = KEY_ENCODER.encodeToString(clientPublicKey.key().getEncoded());
            return (expiry
                            + "-----BEGIN RSA PUBLIC KEY-----\n"
                            + encoded
                            + "\n-----END RSA PUBLIC KEY-----\n")
                    .getBytes(StandardCharsets.US_ASCII);
        }

        byte[] keyData = clientPublicKey.key().getEncoded();
        return ByteBuffer.allocate(keyData.length + UUID_SIZE + MILLISECOND_SIZE)
                .putLong(ownerPremiumId.getMostSignificantBits())
                .putLong(ownerPremiumId.getLeastSignificantBits())
                .putLong(clientPublicKey.expire().toEpochMilli())
                .put(keyData)
                .array();
    }

    /**
     * Verifies that the decrypted client nonce matches the expected verification token.
     *
     * @param expected the expected token bytes
     * @param decryptionKey the server private key
     * @param encryptedNonce the encrypted nonce payload
     * @return {@code true} if decrypted nonce matches expected; {@code false} otherwise
     * @throws NoSuchPaddingException if padding is invalid
     * @throws IllegalBlockSizeException if block size is invalid
     * @throws NoSuchAlgorithmException if algorithm is unavailable
     * @throws BadPaddingException if decryption padding fails
     * @throws InvalidKeyException if key is invalid
     */
    public static boolean verifyNonce(
            byte[] expected, PrivateKey decryptionKey, byte[] encryptedNonce)
            throws NoSuchPaddingException,
                    IllegalBlockSizeException,
                    NoSuchAlgorithmException,
                    BadPaddingException,
                    InvalidKeyException {
        byte[] decryptedNonce = decrypt(decryptionKey, encryptedNonce);
        return MessageDigest.isEqual(expected, decryptedNonce);
    }

    /**
     * Verifies a client-signed nonce using salt and signature.
     *
     * @param nonce the original nonce bytes
     * @param clientKey the client's public key
     * @param signatureSalt the signature salt
     * @param signature the signature bytes
     * @return {@code true} if valid; {@code false} otherwise
     * @throws NoSuchAlgorithmException if algorithm is unavailable
     * @throws InvalidKeyException if client key is invalid
     * @throws SignatureException if signature verification fails
     */
    public static boolean verifySignedNonce(
            byte[] nonce, PublicKey clientKey, long signatureSalt, byte[] signature)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(clientKey);
        verifier.update(nonce);
        verifier.update(Longs.toByteArray(signatureSalt));
        return verifier.verify(signature);
    }

    private static PublicKey loadMojangSessionKey()
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        var keyUrl =
                PaperBootstrap.class.getClassLoader().getResource("yggdrasil_session_pubkey.der");
        var keyData = Resources.toByteArray(keyUrl);
        var keySpec = new X509EncodedKeySpec(keyData);

        return KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }

    private static byte[] decrypt(PrivateKey key, byte[] data)
            throws NoSuchPaddingException,
                    NoSuchAlgorithmException,
                    InvalidKeyException,
                    IllegalBlockSizeException,
                    BadPaddingException {
        Cipher cipher = Cipher.getInstance(key.getAlgorithm());
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    private static byte[] getServerIdHash(
            String sessionId, PublicKey publicKey, SecretKey sharedSecret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(sessionId.getBytes(StandardCharsets.ISO_8859_1));
            digest.update(sharedSecret.getEncoded());
            digest.update(publicKey.getEncoded());
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 digest not available", e);
        }
    }
}
