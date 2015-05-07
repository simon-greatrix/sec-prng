package prng.seeds;

import java.io.File;
import java.util.Map;

import org.mapdb.CC;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.mapdb.Store;
import org.mapdb.StoreDirect;
import org.mapdb.StoreWAL;
import org.mapdb.Volume;

public class MapTest {
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
            System.out.println("chunkSize = "+chunkSize);
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

    public static void main(String[] args) {
        String fileName = "map_test2.db";
        DB db = new Maker()._newFileDB(new File(fileName)).checksumEnable().closeOnJvmShutdown().chunkSize(8192).make();
        //db.compact();
        Map<String,String> map = db.createHashMap("seed-data").keySerializer(Serializer.STRING).valueSerializer(
                Serializer.STRING).makeOrGet();
        map.put("fred", "1");
        map.put("barney", "2");
        map.put("wilma", "3");
        map.put("betty", "4");
        Store.forDB(db).printStatistics();
    }

}
