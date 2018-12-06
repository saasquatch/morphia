package xyz.morphia.mapping;


import org.bson.types.ObjectId;
import org.junit.Test;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Id;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MapperOptionsTest extends TestBase {

    @Test
    public void emptyListStoredWithOptions() {
        final HasList hl = new HasList();

        hl.names = null;
        //Test default behavior
        getMapper().getOptions().setStoreNulls(false);
        shouldNotFindField(hl);

        hl.names = new ArrayList<>();

        //Test default behavior
        getMapper().getOptions().setStoreEmpties(false);
        shouldNotFindField(hl);

        //Test default storing empty list/array with storeEmpties option
        getMapper().getOptions().setStoreEmpties(true);
        shouldFindField(hl, new ArrayList<>());
    }

    @Test
    public void emptyMapStoredWithOptions() {
        final HasMap hm = new HasMap();
        hm.properties = new HashMap<>();

        //Test default behavior
        getMapper().getOptions().setStoreEmpties(false);
        shouldNotFindField(hm);

        //Test default storing empty map with storeEmpties option
        getMapper().getOptions().setStoreEmpties(true);
        shouldFindField(hm, new HashMap<>());
    }

    @Test
    public void emptyCollectionValuedMapStoredWithOptions() {
        final HasCollectionValuedMap hm = new HasCollectionValuedMap();
        hm.properties = new HashMap<>();

        //Test default behavior
        getMapper().getOptions().setStoreEmpties(false);
        shouldNotFindField(hm);

        //Test default storing empty map with storeEmpties option
        getMapper().getOptions().setStoreEmpties(true);
        shouldFindField(hm, new HashMap<>());
    }

    @Test
    public void lowercaseDefaultCollection() {
        DummyEntity entity = new DummyEntity();

        String collectionName = getMapper().getCollectionName(entity.getClass());
        assertEquals("uppercase", "DummyEntity", collectionName);

        getMapper().getOptions().setUseLowerCaseCollectionNames(true);

        collectionName = getMapper().getCollectionName(entity.getClass());
        assertEquals("lowercase", "dummyentity", collectionName);
    }

    @Test
    public void nullListStoredWithOptions() {
        final HasList hl = new HasList();
        hl.names = null;

        //Test default behavior
        getMapper().getOptions().setStoreNulls(false);
        shouldNotFindField(hl);

        //Test default storing null list/array with storeNulls option
        getMapper().getOptions().setStoreNulls(true);
        shouldFindField(hl, null);

        //Test opposite from above
        getMapper().getOptions().setStoreNulls(false);
        shouldNotFindField(hl);
    }

    @Test
    public void nullMapStoredWithOptions() {
        final HasMap hm = new HasMap();
        hm.properties = null;

        //Test default behavior
        getMapper().getOptions().setStoreNulls(false);
        shouldNotFindField(hm);

        //Test default storing empty map with storeEmpties option
        getMapper().getOptions().setStoreNulls(true);
        shouldFindField(hm, null);


        //Test opposite from above
        getMapper().getOptions().setStoreNulls(false);
        shouldNotFindField(hm);
    }

    private void shouldFindField(final HasList hl, final List<String> expected) {
        getDatastore().save(hl);
        assertEquals(expected, getDatastore().find(HasList.class).first().names);
    }

    private void shouldFindField(final HasMap hl, final Map<String, String> expected) {
        getDatastore().save(hl);
        assertEquals(expected, getDatastore().find(HasMap.class).first().properties);
    }

    private void shouldFindField(final HasCollectionValuedMap hm, final Map<String, Collection<String>> expected) {
        getDatastore().save(hm);
        assertEquals(expected, getDatastore().find(HasCollectionValuedMap.class).first().properties);
    }

    private void shouldNotFindField(final HasMap hl) {
        getDatastore().save(hl);
        assertNull(getDatastore().find(HasMap.class).first().properties);
    }

    private void shouldNotFindField(final HasList hl) {
        getDatastore().save(hl);
        assertNull(getDatastore().find(HasList.class).first().names);
    }

    private void shouldNotFindField(final HasCollectionValuedMap hm) {
        getDatastore().save(hm);
        assertNull(getDatastore().find(HasCollectionValuedMap.class).first().properties);
    }

    private static class HasList {
        @Id
        private ObjectId id = new ObjectId();
        private List<String> names;

        HasList() {
        }
    }

    private static class HasMap {
        @Id
        private ObjectId id = new ObjectId();
        private Map<String, String> properties;

        HasMap() {
        }
    }

    private static class HasCollectionValuedMap {
        @Id
        private ObjectId id = new ObjectId();
        private Map<String, Collection<String>> properties;

        HasCollectionValuedMap() {
        }
    }

    private static class HasComplexObjectValuedMap {
        @Id
        private ObjectId id = new ObjectId();
        private Map<String, ComplexObject> properties;

        HasComplexObjectValuedMap() {
        }
    }

    @Entity
    private static class DummyEntity {
        @Id
        private ObjectId id = new ObjectId();
    }

    private static class ComplexObject {
        private String stringVal;
        private int intVal;
    }
}
