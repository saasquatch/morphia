package xyz.morphia.mapping;


import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Id;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class URIMappingTest extends TestBase {

    @Test
    public void testURIField() throws Exception {
        final ContainsURI entity = new ContainsURI();
        final URI testURI = new URI("http://lamest.local/test.html");

        entity.uri = testURI;
        getDatastore().save(entity);
        final ContainsURI loaded = getDatastore().find(ContainsURI.class).first();
        Assert.assertNotNull(loaded.uri);
        Assert.assertEquals(testURI, loaded.uri);

    }

    @Test
    public void testURIMap() throws Exception {
        final ContainsURIKeyedMap entity = new ContainsURIKeyedMap();
        final URI testURI = new URI("http://lamest.local/test.html");

        entity.uris.put(testURI, "first");
        getDatastore().save(entity);
        final ContainsURIKeyedMap loaded = getDatastore().find(ContainsURIKeyedMap.class).first();
        Assert.assertNotNull(loaded.uris);
        Assert.assertEquals(1, loaded.uris.size());
        Assert.assertEquals(testURI, loaded.uris.keySet().iterator().next());

    }

    private static class ContainsURI {
        @Id
        private ObjectId id;
        private URI uri;
    }

    private static class ContainsURIKeyedMap {
        private final Map<URI, String> uris = new HashMap<>();
        @Id
        private ObjectId id;
    }
}
