package prng.seeds;

import java.io.File;
import java.io.IOError;
import java.util.Map;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import prng.Config;

/**
 * MapDB based sees data storage.
 * 
 * @author Simon Greatrix
 *
 */
public class MapDBStorage extends SeedStorage {
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
        File file = new File(fileName);
        if( file.exists() ) {
            // TODO backup
        }
        db_ = DBMaker.newFileDB(new File(fileName)).checksumEnable().closeOnJvmShutdown().mmapFileEnableIfSupported().make();
        map_ = db_.createHashMap("seed-data").keySerializer(Serializer.STRING).valueSerializer(
                Serializer.BYTE_ARRAY).makeOrGet();
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
    public byte[] getRaw(String name) throws StorageException {
        try {
            return map_.get(name);
        } catch (IOError e) {
            throw new StorageException("MapDB failure", e.getCause());
        }
    }
    @Override
    protected void remove(String name) {
        try {
            map_.remove(name);
        } catch (IOError e) {
            LOG.error("Failed to remove {} from storage",name,e);
        }
    }

}
