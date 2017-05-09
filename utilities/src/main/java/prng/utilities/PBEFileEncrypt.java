package prng.utilities;

import java.io.*;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.*;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.Mac;

/**
 * Utility to encrypt a set of files using PBE.
 *
 * @author Simon Greatrix
 *
 */
public class PBEFileEncrypt {

    /**
     * Include a class in the output Jar
     *
     * @param jarOutput
     *            the Jar
     * @param cl
     *            the class to include
     * @throws IOException
     */
    static void includeClass(JarOutputStream jarOutput, Class<?> cl)
            throws IOException {
        String name = cl.getName().replace('.', '/') + ".class";
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.DEFLATED);
        jarOutput.putNextEntry(entry);

        byte[] buffer = new byte[10000];
        try (InputStream in = cl.getResourceAsStream("/" + name)) {
            int r;
            while( (r = in.read(buffer)) != -1 ) {
                jarOutput.write(buffer, 0, r);
            }
        }

        jarOutput.closeEntry();
    }


    /**
     * Command line entry point
     *
     * @param args
     *            arguments
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public static void main(String[] args)
            throws GeneralSecurityException, IOException {
        PBEFileEncrypt instance = new PBEFileEncrypt(args);
        instance.exec();
    }

    /** Input arguments */
    String[] args;

    /** Input files */
    File[] inputFiles;

    /** Manifest for output jar file */
    Manifest manifest;

    /** Output file */
    File outputFile;

    /** The password */
    char[] password;

    /** PBE specifications for data files */
    PBEItem[] pbeItems;

    /** CRC-32s of the encrypted files */
    long[] crc32;

    /** PBE specification for meta file */
    PBEItem pbeMeta;

    /** Random data */
    SecureRandom rand;


    /**
     * Create new instance
     *
     * @param args
     *            command line arguments
     * @throws GeneralSecurityException
     */
    public PBEFileEncrypt(String[] args) throws GeneralSecurityException {
        this.args = args;
        rand = SecureRandom.getInstanceStrong();
    }


    /**
     * Check the input arguments are valid
     */
    void checkArgs() {
        if( args.length < 2 ) {
            System.err.println("Usage:\n\njava "
                    + PBEFileEncrypt.class.getName()
                    + " <output file> <input file> [<input file 2>...]\n");
            System.exit(1);
        }

        // do not overwrite previous output
        String outputFileName = args[0];
        outputFile = new File(outputFileName);
        if( outputFile.exists() ) {
            System.err.println("File \"" + outputFile.getAbsolutePath()
                    + "\" already exists.");
            System.exit(1);
        }

        // try creating output
        try {
            outputFile.createNewFile();
        } catch (IOException ioe) {
            System.err.println("Cannot create file \""
                    + outputFile.getAbsolutePath() + "\".");
            System.exit(1);
        }
        if( !outputFile.canWrite() ) {
            // try deleting it
            outputFile.delete();
            System.err.println("Cannot write to file \""
                    + outputFile.getAbsolutePath() + "\".");
            System.exit(1);
        }

        // Check out the input files
        inputFiles = new File[args.length - 1];
        for(int i = 1;i < args.length;i++) {
            String inputFileName = args[i];
            File inputFile = new File(inputFileName);
            if( !inputFile.canRead() ) {
                System.err.println("Cannot read file \""
                        + inputFile.getAbsolutePath() + "\".");
                System.exit(1);
            }
            inputFiles[i - 1] = inputFile.getAbsoluteFile();
        }
    }


    /**
     * Create the executable Jar manifest.
     */
    void createManifest() {
        System.console().format("Initialising output archive %s\n\n",
                outputFile.getPath());
        manifest = new Manifest();
        Attributes attr = manifest.getMainAttributes();
        Class<?> cl = PBEFileDecrypt.class;
        attr.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attr.put(Attributes.Name.MAIN_CLASS, cl.getName());
    }


    /**
     * Run the utility
     *
     * @throws GeneralSecurityException
     * @throws IOException
     */
    void exec() throws GeneralSecurityException, IOException {
        checkArgs();
        getPassword();
        initItems();
        createManifest();

        try (JarOutputStream jarOutput = new JarOutputStream(
                new FileOutputStream(outputFile), manifest)) {
            jarOutput.setLevel(Deflater.BEST_COMPRESSION);

            includeClass(jarOutput, PBEFileDecrypt.class);
            includeClass(jarOutput, PBEItem.class);
            includeClass(jarOutput, MacInputStream.class);

            // write the files
            for(int i = 0;i < inputFiles.length;i++) {
                String name = String.format(PBEFileDecrypt.DATA_RESOURCE, i);
                File tmp = prepareFile(i);
                storeFile(jarOutput, name, i, tmp);
            }

            // encrypted meta information
            storeMeta(jarOutput, 1, pbeMeta, pbeItems);
            storeMeta(jarOutput, 0, null, new PBEItem[] { pbeMeta });
        } catch (IOException ioe) {
            System.err.println("Failed to write to encrypted archive");
            ioe.printStackTrace();
        }

    }


    /**
     * Store a prepared file in the jar
     * 
     * @param jarOutput
     *            the jar
     * @param name
     *            the entry name
     * @param index
     *            the input file index
     * @param tmp
     *            the temporary file (will be deleted)
     * @throws IOException
     */
    void storeFile(JarOutputStream jarOutput, String name, int index, File tmp)
            throws IOException {
        System.console().format("Storing file %s\n",
                inputFiles[index].getPath());
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setCrc(crc32[index]);
        entry.setSize(tmp.length());
        entry.setCompressedSize(tmp.length());

        jarOutput.putNextEntry(entry);

        byte[] buffer = new byte[0x10000];
        try (InputStream in = new FileInputStream(tmp)) {
            int r;
            while( (r = in.read(buffer)) != -1 ) {
                jarOutput.write(buffer, 0, r);
            }
        }

        tmp.delete();
        jarOutput.closeEntry();
    }


    /**
     * Store a set of meta information in the JAR
     * 
     * @param jarOutput
     *            the jar
     * @param id
     *            the id of the meta information
     * @param outer
     *            the meta information on the meta information (if any)
     * @param items
     *            the meta information
     * @throws IOException
     * @throws GeneralSecurityException
     */
    void storeMeta(JarOutputStream jarOutput, int id, PBEItem outer,
            PBEItem[] items) throws IOException, GeneralSecurityException {
        System.console().format("Storing meta information part %d\n", id);

        // Create the binary representation of the PBE Items.
        ByteArrayOutputStream metaOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(metaOut);
        dataOut.writeInt(items.length);
        for(int i = 0;i < items.length;i++) {
            // append binary representation
            items[i].writeTo(dataOut);
        }
        dataOut.flush();
        byte[] itemBytes = metaOut.toByteArray();

        // encrypt if required
        if( outer != null ) {
            Mac mac = outer.getMac();
            mac.update(itemBytes);
            outer.setMac(mac);
            Cipher cipher = outer.createCipher(password, Cipher.ENCRYPT_MODE);
            itemBytes = cipher.doFinal(itemBytes);
        }

        // get CRC
        CRC32 crc = new CRC32();
        crc.update(itemBytes);

        // Create zip entry
        ZipEntry entry = new ZipEntry(
                String.format(PBEFileDecrypt.META_RESOURCE, id));
        entry.setMethod(ZipEntry.STORED);
        entry.setCrc(crc.getValue());
        entry.setSize(itemBytes.length);
        entry.setCompressedSize(itemBytes.length);

        // store data
        jarOutput.putNextEntry(entry);
        jarOutput.write(itemBytes);
        jarOutput.closeEntry();
    }


    /**
     * Get the password from the console
     */
    void getPassword() {
        password = PBEFileDecrypt.getPassword();
    }


    /**
     * Initialise the PBE items
     *
     * @throws GeneralSecurityException
     */
    void initItems() throws GeneralSecurityException {
        Path cwd = new File(System.getProperty("user.dir")).toPath();

        pbeItems = new PBEItem[inputFiles.length];

        // For each file we are processing...
        for(int i = 0;i < inputFiles.length;i++) {
            // Relativize the path
            Path path = cwd.relativize(inputFiles[i].toPath());
            pbeItems[i] = new PBEItem(path.toString(), rand);
        }
        pbeMeta = new PBEItem("", rand);
        crc32 = new long[inputFiles.length];
    }


    /**
     * Encrypt a file
     * 
     * @param index
     *            the file's index
     * @return the prepared file
     * @throws GeneralSecurityException
     * @throws IOException
     */
    @SuppressWarnings("resource")
    File prepareFile(int index) throws GeneralSecurityException, IOException {
        File input = inputFiles[index];
        System.console().format("Preparing file %s\n", input.getPath());
        Cipher cipher = pbeItems[index].createCipher(password,
                Cipher.ENCRYPT_MODE);
        Mac mac = pbeItems[index].getMac();
        CRC32 crc = new CRC32();

        File output = File.createTempFile("tmp_", ".sec",
                outputFile.getParentFile());
        try (OutputStream fileOut = new FileOutputStream(output)) {
            // output is check-summed
            OutputStream out = new CheckedOutputStream(fileOut, crc);

            // output is encrypted
            out = new CipherOutputStream(out, cipher);

            // output is compressed
            out = new GZIPOutputStream(out);

            byte[] buffer = new byte[0x10000];
            try (InputStream fileIn = new FileInputStream(input)) {
                // Input is authenticated
                InputStream in = new MacInputStream(fileIn, mac);

                int r;
                while( (r = in.read(buffer)) != -1 ) {
                    out.write(buffer, 0, r);
                }
            }

            out.close();
        }

        crc32[index] = crc.getValue();
        pbeItems[index].setMac(mac);

        return output;
    }
}
