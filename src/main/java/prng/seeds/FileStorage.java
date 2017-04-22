package prng.seeds;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import prng.utility.BLOBPrint;
import prng.utility.Config;

/**
 * Store all seed information in a single file and load it all into memory when
 * needed.
 * 
 * @author Simon Greatrix
 *
 */
public class FileStorage extends SeedStorage {
    /** File name where data is stored */
    private File fileName_;

    /** Internal map of storage */
    private Map<String, byte[]> storage_ = new HashMap<String, byte[]>();

    /** Lock on file whilst the store is open */
    FileLock lock_ = null;

    /** Channel for store whilst it is open */
    FileChannel channel_ = null;
    
    /** Is the store modified? */
    boolean isModified_ = false;


    /**
     * Create new file storage instance
     */
    public FileStorage() {
        Config config = Config.getConfig("config", FileStorage.class);
        fileName_ = new File(Config.expand(config.get("file", "./seeds.db")));
    }


    /**
     * Initialise this instance, loading the stored seed data
     * 
     * @throws StorageException
     */
    private void init() throws StorageException {
        if( lock_ != null ) return;

        try {
            File file = fileName_;
            LOG.info("Opening file \"{}\"", file.getAbsolutePath());
            channel_ = FileChannel.open(file.toPath(),
                    StandardOpenOption.CREATE, StandardOpenOption.READ,
                    StandardOpenOption.WRITE, StandardOpenOption.DSYNC);

            // this lock is automatically released when the channel is closed
            lock_ = channel_.lock();

            byte[] buf = new byte[(int) channel_.size()];
            storage_.clear();
            if( buf.length == 0 ) return;

            ByteBuffer bbuf = ByteBuffer.wrap(buf);
            channel_.position(0);
            while( bbuf.hasRemaining() ) {
                channel_.read(bbuf);
            }

            ByteArrayInputStream in = new ByteArrayInputStream(buf);
            DataInputStream data = new DataInputStream(in);

            while( true ) {
                boolean flag = data.readBoolean();
                if( flag ) break;
                LOG.info("Reading item {}", Integer.valueOf(storage_.size()));

                // get the key
                String key = data.readUTF();

                int len = data.readUnsignedShort();
                byte[] value = new byte[len];
                data.readFully(value);

                if( LOG.isDebugEnabled() ) {
                    LOG.info("Value for {} is:\n{}", key,
                            BLOBPrint.toString(value));
                }
                storage_.put(key, value);
            }
            isModified_ = false;

            LOG.info("File read finised");
        } catch (IOException ioe) {
            throw new StorageException("Loading storage from "
                    + fileName_.getAbsolutePath() + " failed", ioe);
        }
    }


    @Override
    protected void putRaw(String name, byte[] data) throws StorageException {
        init();
        if( name.length() > 0x8000 ) {
            throw new StorageException("Maximum key length is 32768, not "
                    + name.length(), new IllegalArgumentException(
                    "Parameter too long"));
        }
        if( data.length > 0x10000 ) {
            throw new StorageException("Maximum data length is 65536, not "
                    + data.length, new IllegalArgumentException(
                    "Parameter too long"));
        }
        isModified_ = true;
        storage_.put(name, data);
    }


    @Override
    protected byte[] getRaw(String name) throws StorageException {
        init();
        return storage_.get(name);
    }


    @Override
    protected void remove(String name) {
        storage_.remove(name);
    }


    @Override
    protected void closeRaw() throws StorageException {
        if( channel_==null || !channel_.isOpen() ) return;
        
        TreeSet<String> keys = new TreeSet<String>(storage_.keySet());
        IOException ioe = null;
        try {
            // if not modified, skip straight to the finally block to close the channel
            if( ! isModified_ ) return;

            ByteArrayOutputStream buf = new ByteArrayOutputStream(4000);
            DataOutputStream data = new DataOutputStream(buf);
            for(String key:keys) {
                // flag is false for not EOF
                LOG.debug("Writing flag");
                data.writeBoolean(false);

                // write the key
                LOG.info("Writing key \"{}\"", key);
                data.writeUTF(key);

                // write the value
                byte[] value = storage_.get(key);
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
            LOG.info("Writing file {}", fileName_.getAbsolutePath());
            channel_.position(0);
            while( bbuf.hasRemaining() ) {
                channel_.write(bbuf);
            }
            channel_.force(true);
            LOG.info("Write complete");
        } catch (IOException ioe2) {
            ioe = ioe2;
        } finally {
            try {
                // closing the channel releases the lock
                channel_.close();
            } catch (IOException ioe2) {
                if( ioe == null ) {
                    ioe = ioe2;
                } else {
                    ioe.addSuppressed(ioe2);
                }
            } finally {
                storage_.clear();
                channel_ = null;
                lock_ = null;
            }
        }

        if( ioe != null ) {
            // delete bad file
            LOG.warn("Deleting bad file \"{}\"", fileName_.getAbsolutePath());
            fileName_.delete();

            // rethrow exception
            throw new StorageException("Failed to save "
                    + fileName_.getAbsolutePath(), ioe);
        }
    }

}
