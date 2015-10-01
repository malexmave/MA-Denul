package de.velcommuta.denul.util;

import android.util.Base64;
import android.util.Log;

import org.spongycastle.crypto.params.AEADParameters;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Wrapper for the horrible BouncyCastle / SpongyCastle API
 */
public class Crypto {
    public static final String TAG = "Crypto";

    // Constants for hybrid encryption header lengths
    private static final int BYTES_HEADER_VERSION     = 1;
    private static final int BYTES_HEADER_ALGO        = 1;
    private static final int BYTES_HEADER_LENGTH_ASYM = 4;

    // Number of bytes of the complete header
    private static final int BYTES_HEADER = BYTES_HEADER_VERSION + BYTES_HEADER_ALGO + BYTES_HEADER_LENGTH_ASYM;

    // Offsets
    private static final int OFFSET_VERSION     = 0;
    private static final int OFFSET_ALGO        = OFFSET_VERSION + BYTES_HEADER_VERSION;
    private static final int OFFSET_LENGTH_ASYM = OFFSET_ALGO + BYTES_HEADER_ALGO;

    // Constants for hybrid encryption header values
    protected static final byte VERSION_1 = 0x00;
    protected static final byte ALGO_RSA_OAEP_SHA256_MGF1_WITH_AES_256_GCM = 0x00;

    // Insert provider
    static {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    ///// Key encoding and decoding
    /**
     * Encode a key (public or private) into a base64 String
     * @param key The Key to encode
     * @return Base64-encoded key
     */
    public static String encodeKey(Key key) {
        return new String(Base64.encode(key.getEncoded(), 0, key.getEncoded().length, Base64.NO_WRAP));
    }

    /**
     * Decode a private key encoded with encodeKey
     * @param encoded The base64-encoded private key
     * @return The decoded private key
     */
    public static PrivateKey decodePrivateKey(String encoded) {
        try {
            KeyFactory kFactory = KeyFactory.getInstance("RSA", new BouncyCastleProvider());
            byte[] keybytes = Base64.decode(encoded, Base64.NO_WRAP);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keybytes);
            return kFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            Log.e(TAG, "decodePrivateKey: Error decoding private key: ", e);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Decode a public key encoded with encodeKey
     * @param encoded The base64-encoded public key
     * @return The decoded public key
     */
    public static PublicKey decodePublicKey(String encoded) {
        try {
            KeyFactory kFactory = KeyFactory.getInstance("RSA", new BouncyCastleProvider());
            byte[] keybytes = Base64.decode(encoded, Base64.NO_WRAP);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keybytes);
            return kFactory.generatePublic(keySpec);
        } catch (Exception e) {
            Log.e(TAG, "decodePublicKey: Error decoding public key: ", e);
            e.printStackTrace();
            return null;
        }
    }

    ///// Key generation
    /**
     * Generate an RSA keypair with the specified bitstrength
     * @param bitstrength An integer giving the bit strength of the generated key pair. Should be
     *                    one of 1024, 2048, 3072, 4096.
     * @return The generated KeyPair object, or null if an error occured
     */
    public static KeyPair generateRSAKeypair(int bitstrength) {
        if (bitstrength != 1024 && bitstrength != 2048 && bitstrength != 3072 && bitstrength != 4096) {
            Log.e(TAG, "generateRSAKeypair: Incorrect bitstrength: " + bitstrength);
            return null;
        }
        try {
            // Get KeyPairGenerator for RSA, using the SpongyCastle provider
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "SC");
            // Initialize with target key size
            keyGen.initialize(bitstrength);
            // Generate the keys
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            Log.e(TAG, "generateRSAKeypair: Keypair generation failed: ", e);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generates a random AES256 key and returns it as a byte[]
     * @return The generated AES256 key
     */
    public static byte[] generateAES256Key() {
        try {
            // Get a key generator instance
            KeyGenerator kgen = KeyGenerator.getInstance("AES", "SC");
            // Request an AES256 key
            kgen.init(256);
            // Generate the actual key
            SecretKey seckey = kgen.generateKey();
            // Return the encoded key
            return seckey.getEncoded();
        } catch (Exception e) {
            Log.e(TAG, "generateAES256Key: Exception occured: ", e);
            e.printStackTrace();
            return null;
        }
    }

    ///// Encryption and Decryption
    /**
     * Encrypt some data using AES in GCM mode with PKCS#7 padding.
     * @param data The data that is to be encrypted
     * @param keyenc The key, as a byte[]
     * @return The encrypted data as a byte[]
     */
    public static byte[] encryptAES(byte[] data, byte[] keyenc) {
        return encryptAES(data, keyenc, null);
    }

    /**
     * Encrypt some data using AES in GCM mode with PKCS#7 padding.
     * @param data The data that is to be encrypted
     * @param keyenc The key, as a byte[]
     * @param header The header (with length field nulled), to be added and as Associated Data
     * @return The encrypted data as a byte[]
     */
    public static byte[] encryptAES(byte[] data, byte[] keyenc, byte[] header) {
        try {
            // Get Cipher instance
            Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding", "SC");
            // Create SecretKey object
            SecretKey key = new SecretKeySpec(keyenc, "AES");
            // Initialize the Cipher object
            aesCipher.init(Cipher.ENCRYPT_MODE, key);
            // Get the algorithm parameters
            AlgorithmParameters params = aesCipher.getParameters();
            // Extract the IV
            byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();
            // Add header to authentication
            if (header != null) {
                aesCipher.updateAAD(header);
            }
            // Perform the encryption
            byte[] encrypted = aesCipher.doFinal(data);
            byte[] returnvalue = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, returnvalue, 0, iv.length);
            System.arraycopy(encrypted, 0, returnvalue, iv.length, encrypted.length);
            return returnvalue;

        } catch (Exception e) {
            Log.e(TAG, "encryptAES: Encoutered Exception during encryption: ", e);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Decrypt a piece of AES256-encrypted data with its key
     * @param datawithiv Data with first bytes representing the IV
     * @param keyenc byte[]-encoded key
     * @return Decrypted data as byte[]
     * @throws BadPaddingException If the padding was bad. This indicates that the ciphertext was
     * tampered with (i.e. the authentication failed)
     */
    public static byte[] decryptAES(byte[] datawithiv, byte[] keyenc) throws BadPaddingException {
        return decryptAES(datawithiv, keyenc, null);
    }

    /**
     * Decrypt a piece of AES256-encrypted data with its key
     * @param datawithiv Data with first bytes representing the IV
     * @param keyenc byte[]-encoded key
     * @param header The header, to be authenticated using AEAD
     * @return Decrypted data as byte[]
     * @throws BadPaddingException If the padding was bad. This indicates that the ciphertext was
     * tampered with (i.e. the authentication failed)
     */
    public static byte[] decryptAES(byte[] datawithiv, byte[] keyenc, byte[] header) throws BadPaddingException {
        try {
            // Get Cipher instance
            Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding", "SC");
            // Create SecretKey object
            SecretKey key = new SecretKeySpec(keyenc, "AES");
            // Split datawithiv into iv and data
            byte[] iv = new byte[16];
            byte[] encrypted = new byte[datawithiv.length -16];
            System.arraycopy(datawithiv, 0, iv, 0, 16);
            System.arraycopy(datawithiv, iv.length, encrypted, 0, datawithiv.length - 16);
            // Initialize cipher
            aesCipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            // Add header for AAD
            if (header != null) {
                aesCipher.updateAAD(header);
            }
            // Perform the decryption
            return aesCipher.doFinal(encrypted);
        } catch (NoSuchPaddingException | InvalidAlgorithmParameterException | NoSuchAlgorithmException
                | IllegalBlockSizeException | NoSuchProviderException | InvalidKeyException e) {
            Log.e(TAG, "decryptAES: An Exception occured during decryption: ", e);
        }
        return null;
    }

    /**
     * Encrypt a piece of data using RSA public key encryption
     * @param data The data to be encrypted
     * @param pubkey The Public Key to use
     * @return The encrypted data
     * @throws IllegalBlockSizeException If the data is too long for the provided public key
     */
    public static byte[] encryptRSA(byte[] data, PublicKey pubkey) throws IllegalBlockSizeException {
        try {
            // Get Cipher instance
            Cipher rsaCipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding", "SC");
            // Initialize cipher
            rsaCipher.init(Cipher.ENCRYPT_MODE, pubkey);
            // Return the encrypted data
            return rsaCipher.doFinal(data);
        } catch (NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException | InvalidKeyException | BadPaddingException e) {
            Log.e(TAG, "encryptRSA: Encountered an Exception: ", e);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalBlockSizeException("Too much data for RSA block");
        }
        return null;
    }

    /**
     * Decrypt RSA-encrypted data with the corresponding private key
     * @param data Encrypted data
     * @param privkey Private key to decrypt the data with
     * @return Decrypted data as byte[]
     * @throws IllegalBlockSizeException If the data is too large to decrypt (what are you doing?)
     * @throws BadPaddingException If the padding was incorrect (data manipulated?)
     */
    public static byte[] decryptRSA(byte[] data, PrivateKey privkey) throws IllegalBlockSizeException, BadPaddingException {
        try {
            Cipher rsaCipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding", "SC");
            rsaCipher.init(Cipher.DECRYPT_MODE, privkey);
            return rsaCipher.doFinal(data);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException e) {
            Log.e(TAG, "decryptRSA: Encountered Exception: ", e);
        }
        return null;
    }

    ///// Hybrid encryption

    /**
     * Perform a hybrid encryption on the provided data, using AES256 and the provided RSA public key.
     * @param data Data
     * @param pubkey One RSA public key
     * @return The encrypted data, or null in case of an error
     */
    public static byte[] encryptHybrid(byte[] data, PublicKey pubkey) {
        // Check that the provided data or key are not null
        if (data == null || pubkey == null ) {
            Log.e(TAG, "encryptHybrid: data or public key is null");
            return null;
        }
        // Generate header (which we need now for AEAD)
        byte[] header = generateHeader(VERSION_1, ALGO_RSA_OAEP_SHA256_MGF1_WITH_AES_256_GCM);
        // Generate symmetric secret key
        byte[] sKey = generateAES256Key();
        // symmetrically encrypt data
        byte[] symCiphertext = encryptAES(data, sKey, header);
        // Check that nothing went wrong
        if (symCiphertext == null) {
            Log.e(TAG, "encryptHybrid: Symmetric encryption failed, aborting");
            return null;
        }
        // Encrypt the key
        byte[] asymCiphertext;
        try {
            asymCiphertext = encryptRSA(sKey, pubkey);
        } catch (IllegalBlockSizeException e) {
            Log.e(TAG, "encryptHybrid: IllegalBlocksizeException during asym. encryption, aborting");
            return null;
        }
        // Check that nothing went wrong
        if (asymCiphertext == null) {
            Log.e(TAG, "encryptHybrid: Asymmetric encryption failed, aborting");
            return null;
        }
        // Generate output array of proper size
        byte[] output = new byte[header.length + asymCiphertext.length + symCiphertext.length];
        header = updateHeader(header, asymCiphertext.length);
        // Write header to output, starting at 0
        System.arraycopy(header, 0, output, 0, header.length);
        // Write asym ciphertext to output, starting after header
        System.arraycopy(asymCiphertext, 0, output, header.length, asymCiphertext.length);
        // Write symCiphertext to output, starting after asymCiphertext
        System.arraycopy(symCiphertext, 0, output, header.length + asymCiphertext.length, symCiphertext.length);
        // Return the output value
        return output;
    }

    /**
     * Decrypts a hybrid-encrypted block of data
     * @param ciphertext hybrid-encrypted data
     * @param privkey private key to decrypt the data with
     * @return The unencrypted data, as a byte[], or null, if something went wrong
     * @throws BadPaddingException If one of the decryptions throws it. Indicates that the authentication checks failed
     */
    public static byte[] decryptHybrid(byte[] ciphertext, PrivateKey privkey) throws BadPaddingException {
        // Check if the ciphertext and key are actually set
        if (ciphertext == null || privkey == null) {
            Log.e(TAG, "decryptHybrid: One of the inputs is null, aborting");
            return null;
        }
        // Retrieve the header
        byte[] header = getHeader(ciphertext);
        // Perform some sanity checks on the header
        if (header[0] != VERSION_1) {
            Log.e(TAG, "decryptHybrid: Unknown version number");
            throw new BadPaddingException("Unknown version number");
        } else if (header[1] != ALGO_RSA_OAEP_SHA256_MGF1_WITH_AES_256_GCM) {
            Log.e(TAG, "decryptHybrid: Unknown algorithm specification");
            throw new BadPaddingException("Unknown algorithm specification");
        }
        // Parse the length of the asymmetrically encrypted ciphertext block from the header
        int asymCiphertextLength = parseAsymCiphertextLength(header);
        // Perform sanity checks
        if (header.length + asymCiphertextLength > ciphertext.length) {
            Log.e(TAG, "decryptHybrid: Incorrect asymCiphertextLength specified");
            throw new BadPaddingException("Incorrect asymCiphertextLength");
        }
        // Retrieve asymCiphertext
        byte[] asymCiphertext = Arrays.copyOfRange(ciphertext, header.length, header.length + asymCiphertextLength);
        // Retrieve symCiphertext
        byte[] symCiphertext = Arrays.copyOfRange(ciphertext, header.length + asymCiphertextLength, ciphertext.length);
        // Decrypt symmetric key from asymCiphertext
        byte[] symKey;
        try {
            symKey = decryptRSA(asymCiphertext, privkey);
        } catch (IllegalBlockSizeException e) {
            Log.e(TAG, "decryptHybrid: Illegal Block Size Exception, aborting");
            return null;
        } catch (ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "decryptHybrid: Array Index out of bounds indicates incorrect length in header, aborting");
            throw new BadPaddingException("Incorrect asymCiphertextLength");
        }
        // Ensure that decryption was successful
        if (symKey == null) {
            Log.e(TAG, "decryptHybrid: Something went wrong during asym. decryption, aborting");
            return null;
        }
        // Null the length field of the header for AAD verification
        header = updateHeader(header, 0);
        // Decrypt symCiphertext
        byte[] cleartext = decryptAES(symCiphertext, symKey, header);
        // Ensure that decryption was successful
        if (cleartext == null) {
            Log.e(TAG, "decryptHybrid: Something went wrong during sym. decryption, aborting");
            return null;
        }
        // Return decrypted data
        return cleartext;
    }

    // Helper functions for header generation and parsing
    /**
     * Generate a header for a hybrid-encrypted packet
     * @param asymCipherLength Length of the asymmetrically encrypted ciphertext
     * @param version Version number, as byte (use the constants provided by this class)
     * @param algo Algorithm descriptor, as byte (use the constants provided by this class)
     * @return The header, as a byte[]
     */
    protected static byte[] generateHeader(byte version, byte algo, int asymCipherLength) {
        // Create a new byte[] for the header
        byte[] header = new byte[BYTES_HEADER];
        // Set version and algorithm
        header[0] = version;
        header[1] = algo;
        // Set length of asymmetrically enciphered ciphertext
        byte[] asymCipherLengthBytes = ByteBuffer.allocate(4).putInt(asymCipherLength).array();
        System.arraycopy(asymCipherLengthBytes, 0, header, OFFSET_LENGTH_ASYM, asymCipherLengthBytes.length);
        return header;
    }

    /**
     * Generate a header for a hybrid-encrypted packet
     * @param version Version number, as byte (use the constants provided by this class)
     * @param algo Algorithm descriptor, as byte (use the constants provided by this class)
     * @return The header, as a byte[]
     */
    protected static byte[] generateHeader(byte version, byte algo) {
        // Create a new byte[] for the header
        byte[] header = new byte[BYTES_HEADER];
        // Set version and algorithm
        header[0] = version;
        header[1] = algo;
        return header;
    }

    /**
     * Update an existing header with the length information
     * @param header Existing header
     * @param asymCipherLength Length of asymmetric ciphertext
     * @return Updated header
     */
    protected static byte[] updateHeader(byte[] header, int asymCipherLength) {
        byte[] asymCipherLengthBytes = ByteBuffer.allocate(4).putInt(asymCipherLength).array();
        System.arraycopy(asymCipherLengthBytes, 0, header, OFFSET_LENGTH_ASYM, asymCipherLengthBytes.length);
        return header;
    }

    /**
     * Get the header from an encrypted blob of data
     * @param message The whole message (headers plus encrypted contents)
     * @return The header
     * @throws BadPaddingException If the message is shorter than the header
     */
    protected static byte[] getHeader(byte[] message) throws BadPaddingException {
        if (message.length < BYTES_HEADER) {
            Log.e(TAG, "getHeader: message shorter than header, aborting");
            throw new BadPaddingException("Incorrect header");
        }
        return Arrays.copyOfRange(message, 0, BYTES_HEADER);
    }

    /**
     * Parses the length of the asymmetrically encrypted ciphertext from the header
     * @param header The full header
     * @return The length, as int
     * @throws BadPaddingException If the header has an incorrect length
     */
    protected static int parseAsymCiphertextLength(byte[] header) throws BadPaddingException{
        if (header.length != BYTES_HEADER) {
            Log.e(TAG, "parseAsymCiphertextLength: Malformed header");
            throw new BadPaddingException("Malformed hybrid header");
        }
        return ByteBuffer.wrap(
                Arrays.copyOfRange(header, OFFSET_LENGTH_ASYM, OFFSET_LENGTH_ASYM + BYTES_HEADER_LENGTH_ASYM)
        ).getInt();
    }
}