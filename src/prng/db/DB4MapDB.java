package prng.db;

import java.io.File;

import org.mapdb.DBMaker;

import prng.Config;

public class DB4MapDB extends DB {
    private final org.mapdb.DB db_;
    
    public DB4MapDB() {
        Config config = Config.getConfig("",DB.class);
        String fileName = Config.expand( config.get("file") );
        db_ = DBMaker.newFileDB(new File(fileName)).checksumEnable().closeOnJvmShutdown().mmapFileEnableIfSupported().make();
    }

}
