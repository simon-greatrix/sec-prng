package prng.seeds;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import prng.SecureRandomProvider;
import prng.config.Config;
import prng.utility.BLOBPrint;

/**
 * Store all seed information in a single file and load it all into memory when
 * needed.
 * 
 * @author Simon Greatrix
 *
 */
public class FileStorage extends SeedStorage {
    /** File name where data is stored */
    final File fileName;

    /** Internal map of storage */
    private Map<String, byte[]> storage = new HashMap<String, byte[]>();

    /** Lock on file whilst the store is open */
    FileLock lock = null;

    /** Channel for store whilst it is open */
    FileChannel channel = null;

    /** Is the store modified? */
    boolean isModified = false;


    /**
     * Create new file storage instance
     * 
     * @throws StorageException if access to the file is denied
     */
    public FileStorage() throws StorageException {
        Config config = Config.getConfig("config", FileStorage.class);
        String file = config.get("file", "./seeds.db");
        file = Config.expand(file);
        
        fileName = new File(file);
        try {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {

                @Override
                public Void run() {
                    if( !fileName.canWrite() ) {
                        LOG.error("Cannot write to file \""+fileName.getAbsolutePath()+"\".");
                    }
                    if( fileName.exists() && ! fileName.canRead() ) {
                        LOG.error("Cannot read from file \""+fileName.getAbsolutePath()+"\".");
                    }
                    return null;
                }
                
            });
        } catch ( SecurityException se ) {
            SecureRandomProvider.LOG.warn(
                    "Lacking permission: 'FilePermission \""+fileName.getAbsolutePath()+"\", \"delete,write,read\"' - cannot access seed data in file");
            throw new StorageException(
                    "Privilege 'FilePermission \""+fileName.getAbsolutePath()+"\", \"delete,write,read\"' is required to use seed file.",
                    se);
        }
    }


    /**
     * Initialise this instance, loading the stored seed data
     * 
     * @throws StorageException if reading the file fails
     */
    private void init() throws StorageException {
        if( lock != null ) return;

        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {

                @Override
                public Void run() throws StorageException {
                    initWithPrivilege();
                    return null;
                }
                
            });
        } catch (PrivilegedActionException e) {
            // Storage access failed
            StorageException se = (StorageException) e.getCause();
            throw se;
        } catch (SecurityException se ) {
            // Lacking required privilege
            SecureRandomProvider.LOG.warn(
                    "Lacking permission: 'FilePermission \""+fileName.getAbsolutePath()+"\", \"delete,write,read\"' - cannot access seed data in file");
            throw new StorageException(
                    "Privilege 'FilePermission \""+fileName.getAbsolutePath()+"\", \"delete,write,read\"' is required to use seed file.",
                    se);            
        }
    }


    /**
     * Initialise this instance having acquired the requisite privilege.
     * 
     * @throws StorageException
     *            if the privileges cannot be accessed
     */
    void initWithPrivilege() throws StorageException {
        try {
            File file = fileName;
            LOG.info("Opening file \"{}\"", file.getAbsolutePath());
            // create the file and folder if needed
            if( !file.exists() ) {
                if( file.getParentFile().mkdirs() ) {
                    LOG.info("Created folder \""
                            + file.getParentFile().getAbsolutePath() + "\"");
                }
                if( file.createNewFile() ) {
                    LOG.info("Created file \"" + file.getAbsolutePath() + "\"");
                }
                if( !file.exists() ) {
                    throw new StorageException("Failed to create file \""
                            + file.getAbsolutePath() + "\"", null);
                }
            }

            // check file is usable
            if( !file.isFile() ) {
                throw new StorageException("File \"" + file.getAbsolutePath()
                        + "\" is not a file.", null);
            }
            if( !file.canWrite() ) {
                throw new StorageException("File \"" + file.getAbsolutePath()
                        + "\" is not writable.", null);
            }
            if( !file.canRead() ) {
                throw new StorageException("File \"" + file.getAbsolutePath()
                        + "\" is not readable.", null);
            }

            channel = FileChannel.open(file.toPath(),
                    StandardOpenOption.CREATE, StandardOpenOption.READ,
                    StandardOpenOption.WRITE, StandardOpenOption.DSYNC);

            // this lock is automatically released when the channel is closed
            lock = channel.lock();

            byte[] buf = new byte[(int) channel.size()];
            storage.clear();
            if( buf.length == 0 ) return;

            ByteBuffer bbuf = ByteBuffer.wrap(buf);
            channel.position(0);
            while( bbuf.hasRemaining() ) {
                channel.read(bbuf);
            }

            ByteArrayInputStream in = new ByteArrayInputStream(buf);
            DataInputStream data = new DataInputStream(in);

            while( true ) {
                boolean flag = data.readBoolean();
                if( flag ) break;
                LOG.debug("Reading item {}", Integer.valueOf(storage.size()));

                // get the key
                String key = data.readUTF();

                int len = data.readUnsignedShort();
                byte[] value = new byte[len];
                data.readFully(value);

                if( LOG.isDebugEnabled() ) {
                    LOG.debug("Value for {} is:\n{}", key,
                            BLOBPrint.toString(value));
                }
                storage.put(key, value);
            }
            isModified = false;

            LOG.info("File read finised");
        } catch (IOException ioe) {
            throw new StorageException("Loading storage from "
                    + fileName.getAbsolutePath() + " failed", ioe);
        }
    }


    @Override
    protected void putRaw(String name, byte[] data) throws StorageException {
        init();
        if( name.length() > 0x8000 ) {
            throw new StorageException(
                    "Maximum key length is 32768, not " + name.length(),
                    new IllegalArgumentException("Parameter too long"));
        }
        if( data.length > 0x10000 ) {
            throw new StorageException(
                    "Maximum data length is 65536, not " + data.length,
                    new IllegalArgumentException("Parameter too long"));
        }
        isModified = true;
        storage.put(name, data);
    }


    @Override
    protected byte[] getRaw(String name) throws StorageException {
        init();
        return storage.get(name);
    }


    @Override
    protected void remove(String name) {
        storage.remove(name);
    }


    @Override
    protected void closeRaw() throws StorageException {
        if( channel == null || !channel.isOpen() ) return;

        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {

                @Override
                public Void run() throws StorageException {
                    closeRawWithPrivilege();
                    return null;
                }
                
            });
        } catch (PrivilegedActionException e) {
            // It will be a storage exception
            StorageException se = (StorageException) e.getCause();
            throw se;
        } catch (SecurityException se) {
            // Lacking required privilege
            SecureRandomProvider.LOG.warn(
                    "Lacking permission: 'FilePermission \""+fileName.getAbsolutePath()+"\", \"delete,write,read\"' - cannot access seed data in file");
            throw new StorageException(
                    "Privilege 'FilePermission \""+fileName.getAbsolutePath()+"\", \"delete,write,read\"' is required to use seed file.",
                    se);            
        }
    }
    
    /**
     * Close the storage after ascerting privilege
     * @throws StorageException
     *             if the preferences cannot be written to
     */
    void closeRawWithPrivilege() throws StorageException {
        TreeSet<String> keys = new TreeSet<String>(storage.keySet());
        IOException ioe = null;
        try {
            // if not modified, skip straight to the finally block to close the
            // channel
            if( !isModified ) return;

            ByteArrayOutputStream buf = new ByteArrayOutputStream(4000);
            DataOutputStream data = new DataOutputStream(buf);
            for(String key:keys) {
                // flag is false for not EOF
                LOG.debug("Writing flag");
                data.writeBoolean(false);

                // write the key
                LOG.debug("Writing key \"{}\"", key);
                data.writeUTF(key);

                // write the value
                byte[] value = storage.get(key);
                if( LOG.isDebugEnabled() ) {
                    LOG.debug("Writing value:\n{}", BLOBPrint.toString(value));
                }
                data.writeShort(value.length);
                data.write(value);
            }
            // flag is true for EOF
            LOG.debug("Writing final flag");
            data.writeBoolean(true);

            // convert to Buffer and write out in one write
            ByteBuffer bbuf = ByteBuffer.wrap(buf.toByteArray());
            LOG.info("Writing file {}", fileName.getAbsolutePath());
            channel.position(0);
            while( bbuf.hasRemaining() ) {
                channel.write(bbuf);
            }
            channel.force(true);
            LOG.info("Write complete");
        } catch (IOException ioe2) {
            ioe = ioe2;
        } finally {
            try {
                // closing the channel releases the lock
                channel.close();
            } catch (IOException ioe2) {
                if( ioe == null ) {
                    ioe = ioe2;
                } else {
                    ioe.addSuppressed(ioe2);
                }
            } finally {
                storage.clear();
                channel = null;
                lock = null;
            }
        }

        if( ioe != null ) {
            // delete bad file
            LOG.warn("Deleting bad file \"{}\"", fileName.getAbsolutePath());
            fileName.delete();

            // rethrow exception
            throw new StorageException(
                    "Failed to save " + fileName.getAbsolutePath(), ioe);
        }
    }

}
