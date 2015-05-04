package prng.seeds;

import java.io.File;
import java.io.IOError;
import java.util.Map;

import org.mapdb.CC;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.mapdb.StoreDirect;
import org.mapdb.StoreWAL;
import org.mapdb.Volume;

import prng.Config;

/**
 * MapDB based sees data storage.
 * 
 * @author Simon Greatrix
 *
 */
public class MapDBStorage extends SeedStorage {
    /**
     * DBMaker that allows one to set the file size increments
     * 
     * @author Simon Greatrix
     */
    static class Maker extends DBMaker<Maker> {

        /**
         * Set the file size increment in bytes.
         * 
         * @param chunkSize
         *            file size increment
         * @return this
         */
        public Maker chunkSize(int chunkSize) {
            if( chunkSize < 4096 ) {
                chunkSize = 4096;
            } else if( chunkSize > 134217728 ) {
                chunkSize = 134217728;
            }
            int shift = 32 - Integer.numberOfLeadingZeros(chunkSize - 1);
            props.setProperty("chunkSize", Integer.toString(shift));
            return getThis();
        }


        @Override
        protected Volume.Factory extendStoreVolumeFactory() {
            long sizeLimit = propsGetLong(Keys.sizeLimit, 0);
            int chunkSize = propsGetInt("chunkSize", CC.VOLUME_CHUNK_SHIFT);
            String volume = props.getProperty(Keys.volume);
            if( Keys.volume_byteBuffer.equals(volume) ) return Volume.memoryFactory(
                    false, sizeLimit, chunkSize);
            else if( Keys.volume_directByteBuffer.equals(volume) )
                return Volume.memoryFactory(true, sizeLimit, chunkSize);

            File indexFile = new File(props.getProperty(Keys.file));

            File dataFile = new File(indexFile.getPath()
                    + StoreDirect.DATA_FILE_EXT);
            File logFile = new File(indexFile.getPath()
                    + StoreWAL.TRANS_LOG_FILE_EXT);

            return Volume.fileFactory(indexFile, propsGetRafMode(),
                    propsGetBool(Keys.readOnly), sizeLimit, chunkSize, 0,
                    dataFile, logFile, propsGetBool(Keys.asyncWrite));
        }
    }
 
    

    /** The storage DB */
    private final DB db_;

    /** Key storage map */
    private final Map<String, byte[]> map_;


    /**
     * Create new storage
     */
    public MapDBStorage() {
        Config config = Config.getConfig("", SeedStorage.class);
        String fileName = Config.expand(config.get("file"));
        db_ = new Maker()._newFileDB(new File(fileName)).checksumEnable().closeOnJvmShutdown().chunkSize(8192).make();
        map_ = db_.createHashMap("seed-data").keySerializer(Serializer.STRING).valueSerializer(
                Serializer.BYTE_ARRAY).makeOrGet();
    }


    @Override
    public void close() {
        db_.commit();
        db_.close();
    }


    @Override
    public byte[] getRaw(String name) throws StorageException {
        try {
            return map_.get(name);
        } catch (IOError e) {
            throw new StorageException("MapDB failure", e.getCause());
        }
    }


    @Override
    protected void putRaw(String name, byte[] data) throws StorageException {
        try {
            map_.put(name, data);
        } catch (IOError e) {
            throw new StorageException("MapDB failure", e.getCause());
        }
    }


    @Override
    protected void remove(String name) {
        try {
            map_.remove(name);
        } catch (IOError e) {
            LOG.error("Failed to remove {} from storage", name, e);
        }
    }

}
