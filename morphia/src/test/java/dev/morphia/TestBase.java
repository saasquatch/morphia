package dev.morphia;


import org.junit.After;
import org.junit.Before;


@SuppressWarnings("deprecation")
public abstract class TestBase {
    private Datastore ds;
    private Morphia morphia = new Morphia();

    protected TestBase() {
    }

    @Before
    public void setUp() {
        this.ds = this.morphia.createDatastore(null, "");
    }

    @After
    public void tearDown() {
        // new ScopedFirstLevelCacheProvider().release();
    }

    public Datastore getDs() {
        return ds;
    }

    public Morphia getMorphia() {
        return morphia;
    }
}
