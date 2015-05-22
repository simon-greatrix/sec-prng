package prng.seeds;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import prng.BLOBPrint;
import prng.Config;

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


    public FileStorage() {
        Config config = Config.getConfig("config", FileStorage.class);
        fileName_ = new File(Config.expand(config.get("file", "./seeds.db")));
    }


    private void read(ByteBuffer buf, int len) throws IOException {
        buf.position(0).limit(len);
        while( len > 0 ) {
            int r = channel_.read(buf);
            if( r == -1 ) throw new EOFException();
            len -= r;
        }
        buf.flip();
    }


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

            ByteBuffer buf = ByteBuffer.allocate(0x10000);
            buf.order(ByteOrder.BIG_ENDIAN);
            CharBuffer chars = buf.asCharBuffer();
            storage_.clear();
            channel_.position(0);

            // if the file is too small, assume empty
            if( channel_.size() < 4 ) return;

            while( true ) {
                read(buf, 1);
                if( buf.get(0) == 0 ) break;
                LOG.info("Reading item {}", Integer.valueOf(storage_.size()));

                // get key length and key
                chars.clear();
                read(buf, 2);
                int len = chars.get(0);
                read(buf, len * 2);
                String key = chars.position(0).limit(len).toString();
                LOG.info("Reading entry {}", key);

                // get value length and value
                read(buf, 2);
                len = chars.get(0);
                read(buf, len);
                byte[] value = new byte[len];
                buf.get(value, 0, len);
                if( LOG.isDebugEnabled() ) {
                    LOG.info("Value for {} is:\n{}", key,
                            BLOBPrint.toString(value));
                }
                storage_.put(key, value);
            }

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


    private void write(ByteBuffer buf, int limit) throws IOException {
        buf.position(0).limit(limit);
        while( buf.hasRemaining() ) {
            channel_.write(buf);
        }
    }


    @Override
    protected void closeRaw() throws StorageException {
        TreeSet<String> keys = new TreeSet<String>(storage_.keySet());
        IOException ioe = null;
        try {
            LOG.info("Writing file {}", fileName_.getAbsolutePath());
            channel_.position(0);
            ByteBuffer buf = ByteBuffer.allocate(0x10000);
            buf.order(ByteOrder.BIG_ENDIAN);
            CharBuffer chars = buf.asCharBuffer();

            for(String key:keys) {
                LOG.debug("Writing flag");
                buf.put(0, (byte) -1);
                write(buf,1);

                LOG.info("Writing key \"{}\"",key);
                chars.put(0, (char) key.length());
                write(buf,2);
                chars.clear();
                chars.put(key);
                buf.position(0).limit(2 * key.length());
                channel_.write(buf);

                byte[] value = storage_.get(key);
                if( LOG.isDebugEnabled() ) {
                    LOG.debug("Writing value:\n{}",BLOBPrint.toString(value));
                }
                chars.put(0, (char) value.length);
                write(buf,2);
                buf.clear();
                buf.put(value);
                write(buf,value.length);
            }
            LOG.debug("Writing final flag");
            buf.put(0, (byte) 0);
            write(buf,1);
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
            LOG.warn("Deleting bad file \"{}\"",fileName_.getAbsolutePath());
            fileName_.delete();

            // rethrow exception
            throw new StorageException("Failed to save "
                    + fileName_.getAbsolutePath(), ioe);
        }
    }

}
