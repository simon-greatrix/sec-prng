package prng.utilities;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A password based encrypted item meta information.
 *
 * @author Simon Greatrix
 *
 */
public class PBEItem {

    /**
     * Read an array of bytes
     *
     * @param input
     *            the input
     * @return the bytes
     * @throws IOException
     */
    private static byte[] readArray(DataInputStream input) throws IOException {
        int len = input.readUnsignedShort();
        byte[] output = new byte[len];
        input.readFully(output);
        return output;
    }


    /**
     * Convert bytes to hexadecimal
     *
     * @param data
     *            the bytes
     * @return the hexadecimal
     */
    static String toHex(byte[] data) {
        if( data == null ) {
            return "null";
        }
        if( data.length == 0 ) {
            return "[ ]";
        }
        StringBuilder buf = new StringBuilder();
        buf.append("[ ");
        for(byte b:data) {
            buf.append(String.format("%02x ", Integer.valueOf(b & 0xff)));
        }
        buf.append("]");
        return buf.toString();
    }


    /**
     * Write an array of bytes
     *
     * @param output
     *            the output
     * @param array
     *            the bytes
     * @throws IOException
     */
    private static void writeArray(DataOutputStream output, byte[] array)
            throws IOException {
        output.writeShort((short) array.length);
        output.write(array);
    }

    /** Name of the PBE cipher algorithm */
    private String cipher;

    /** IV vector for cipher */
    private byte[] iv;

    /** MAC value */
    private byte[] mac;

    /** Algorithm used to derive MAC */
    private String macAlg;

    /** Secret key used with MAC */
    private SecretKey macKey;

    /** Relative path to file */
    private String path;

    /** PBE Iterations used when deriving cipher key for this file */
    private int pbeIterations;

    /** PBE Salt used when deriving cipher key for this file */
    private byte[] pbeSalt;


    /**
     * Load encoded PBE Item
     *
     * @param input
     *            data
     * @throws IOException
     */
    public PBEItem(DataInputStream input) throws IOException {
        int v = input.readByte();
        if( v != 1 ) {
            throw new IOException(
                    "PBEItem encoding version is not version 1 but " + v);
        }
        cipher = input.readUTF();
        iv = readArray(input);
        mac = readArray(input);
        macAlg = input.readUTF();

        byte[] secret = readArray(input);
        macKey = new SecretKeySpec(secret, macAlg);

        path = input.readUTF();
        pbeIterations = input.readInt();
        pbeSalt = readArray(input);
    }


    /**
     * Create new PBE item with default algorithms
     *
     * @param path
     *            path to file
     * @param rand
     *            random number generator for initialising keys
     * @throws GeneralSecurityException
     */
    public PBEItem(String path, SecureRandom rand)
            throws GeneralSecurityException {
        this.path = path;
        cipher = "PBEWithHmacSHA256AndAES_256";
        iv = new byte[16];
        rand.nextBytes(iv);
        setMacAlgorithm("HmacSHA256", rand);
        pbeSalt = new byte[16];
        rand.nextBytes(pbeSalt);
        pbeIterations = 1000 + rand.nextInt(1024);
    }


    /**
     * Create a cipher to use on this item
     *
     * @param password
     *            the password
     * @param mode
     *            the cipher mode
     * @return the cipher instance
     * @throws GeneralSecurityException
     */
    public Cipher createCipher(char[] password, int mode)
            throws GeneralSecurityException {
        PBEKeySpec keySpec = getPBE(password);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(cipher);
        SecretKey sk = factory.generateSecret(keySpec);
        Cipher c = Cipher.getInstance(cipher);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        PBEParameterSpec pbeSpec = new PBEParameterSpec(keySpec.getSalt(),
                keySpec.getIterationCount(), ivSpec);
        c.init(mode, sk, pbeSpec);
        return c;
    }


    /**
     * Get the cipher algorithm name
     *
     * @return the algorithm
     */
    public String getCipher() {
        return cipher;
    }


    /**
     * Get the expected message authentication code
     *
     * @return the code
     */
    public byte[] getExpectedMac() {
        return mac.clone();
    }


    /**
     * Get a new MAC instance for verifying the data associated with this item
     *
     * @return a new MAC instance
     * @throws GeneralSecurityException
     */
    public Mac getMac() throws GeneralSecurityException {
        Mac m = Mac.getInstance(macAlg);
        m.init(macKey);
        return m;
    }


    /**
     * Get the relative path for this item
     *
     * @return the path
     */
    public String getPath() {
        return path;
    }


    /**
     * Create a new PBEKeySpec instance for this item
     *
     * @param password
     *            the associated password
     * @return the key specification
     */
    public PBEKeySpec getPBE(char[] password) {
        return new PBEKeySpec(password, pbeSalt, pbeIterations);
    }


    /**
     * Set the cipher algorithm
     *
     * @param cipherName
     *            the algorithm
     * @param rand
     *            source for initialisation vectors
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     */
    public void setCipher(String cipherName, SecureRandom rand)
            throws GeneralSecurityException {
        cipher = cipherName;
        Cipher c = Cipher.getInstance(cipher);
        int blockSize = c.getBlockSize();
        iv = new byte[blockSize];
        rand.nextBytes(iv);
    }


    /**
     * Set the calculated mac
     *
     * @param m
     *            the calculated mac
     */
    public void setMac(Mac m) {
        mac = m.doFinal();
    }


    /**
     * Set the algorithm to use in calculating the mac
     *
     * @param alg
     *            the algorithm
     * @param rand
     *            source for seed creation
     * @throws GeneralSecurityException
     */
    public void setMacAlgorithm(String alg, SecureRandom rand)
            throws GeneralSecurityException {
        KeyGenerator gen = KeyGenerator.getInstance(alg);
        gen.init(rand);
        macKey = gen.generateKey();
        macAlg = alg;
    }


    /**
     * Set the relative path of this item
     *
     * @param path
     *            the path
     */
    public void setPath(String path) {
        this.path = path;
    }


    /**
     * Set the PBE parameters for this item
     *
     * @param spec
     *            the parameters
     */
    public void setPBE(PBEParameterSpec spec) {
        pbeIterations = spec.getIterationCount();
        pbeSalt = spec.getSalt();
    }


    @Override
    public String toString() {
        return "PBEItem [cipher=" + cipher + ", iv=" + toHex(iv) + ", mac="
                + toHex(mac) + ", macAlg=" + macAlg + ", macKey=" + macKey
                + ", path=" + path + ", pbeIterations=" + pbeIterations
                + ", pbeSalt=" + toHex(pbeSalt) + "]";
    }


    /**
     * Write this item in binary format.
     *
     * @param output
     *            the output stream
     * @throws IOException
     */
    public void writeTo(DataOutputStream output) throws IOException {
        output.writeByte(1);
        output.writeUTF(cipher);
        writeArray(output, iv);
        writeArray(output, mac);
        output.writeUTF(macAlg);
        writeArray(output, ((SecretKeySpec) macKey).getEncoded());
        output.writeUTF(path);
        output.writeInt(pbeIterations);
        writeArray(output, pbeSalt);
    }
}
