package dev.morphia.ext.guice;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import dev.morphia.AdvancedDatastore;
import dev.morphia.Datastore;
import dev.morphia.Morphia;
import dev.morphia.mapping.MappedClass;

@SuppressWarnings("deprecation")
public abstract class TestBase {
    private final MongoClient mongoClient;
    private final Morphia morphia = new Morphia();

    private DB db;
    private Datastore ds;
    private AdvancedDatastore ads;

    protected TestBase() {
        mongoClient = new MongoClient();
    }

    public AdvancedDatastore getAds() {
        return ads;
    }

    public DB getDb() {
        return db;
    }

    public Datastore getDs() {
        return ds;
    }

    public Morphia getMorphia() {
        return morphia;
    }

    @Before
    public void setUp() {
        db = mongoClient.getDB("morphia_test");
        ds = morphia.createDatastore(this.mongoClient, this.db.getName());
        ads = (AdvancedDatastore) this.ds;
    }

    @After
    public void tearDown() {
        cleanup();
        mongoClient.close();
    }

    protected void cleanup() {
        DB db = getDb();
        if (db != null) {
            db.dropDatabase();
        }
    }
}
