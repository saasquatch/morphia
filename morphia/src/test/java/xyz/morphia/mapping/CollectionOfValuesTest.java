package xyz.morphia.mapping;


import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.Datastore;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Id;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;

public class CollectionOfValuesTest extends TestBase {

    @Test
    public void testCity() {
        getMapper().map(City.class);

        City city = new City();
        city.name = "My city";
        city.array = new byte[]{4, 5};
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                city.cells[i][j] = (i * 100 + j);
            }
        }

        getDatastore().save(city);
        final Datastore datastore = getDatastore();
        City loaded = datastore.find(city.getClass()).filter("_id", datastore.getMapper().getId(city)).first();
        Assert.assertEquals(city.name, loaded.name);
        assertArrayEquals(city.array, loaded.array);
        for (int i = 0; i < city.cells.length; i++) {
            assertArrayEquals(city.cells[i], loaded.cells[i]);
        }
    }

    @Test
    public void testListOfListMapping() {
        getMapper().map(ContainsListOfList.class);
        getDatastore().delete(getDatastore().find(ContainsListOfList.class));
        final ContainsListOfList entity = new ContainsListOfList();

        entity.strings = new ArrayList<>();
        entity.strings.add(Arrays.asList("element1", "element2"));
        entity.strings.add(Collections.singletonList("element3"));
        entity.integers = new ArrayList<>();
        entity.integers.add(Arrays.asList(1, 2));
        entity.integers.add(Collections.singletonList(3));
        getDatastore().save(entity);

        final Datastore datastore = getDatastore();
        final ContainsListOfList loaded = datastore.find(entity.getClass())
                                                   .filter("_id", datastore.getMapper().getId(entity))
                                                   .first();

        Assert.assertNotNull(loaded.strings);
        Assert.assertEquals(entity.strings, loaded.strings);
        Assert.assertEquals(entity.strings.get(0), loaded.strings.get(0));
        Assert.assertEquals(entity.strings.get(0).get(0), loaded.strings.get(0).get(0));

        Assert.assertNotNull(loaded.integers);
        Assert.assertEquals(entity.integers, loaded.integers);
        Assert.assertEquals(entity.integers.get(0), loaded.integers.get(0));
        Assert.assertEquals(entity.integers.get(0).get(0), loaded.integers.get(0).get(0));

        Assert.assertNotNull(loaded.id);
    }

    @Test
    public void testTwoDimensionalArrayMapping() {
        getMapper().map(ContainsTwoDimensionalArray.class);
        final ContainsTwoDimensionalArray entity = new ContainsTwoDimensionalArray();
        entity.oneDimArray = "Joseph".getBytes();
        entity.twoDimArray = new byte[][]{"Joseph".getBytes(), "uwe".getBytes()};
        getDatastore().save(entity);
        final ContainsTwoDimensionalArray loaded = getDatastore().find(ContainsTwoDimensionalArray.class).filter("_id", entity.id).first();
        Assert.assertNotNull(loaded.id);
        Assert.assertNotNull(loaded.oneDimArray);
        Assert.assertNotNull(loaded.twoDimArray);

        assertArrayEquals(entity.oneDimArray, loaded.oneDimArray);

        assertArrayEquals(entity.twoDimArray[0], loaded.twoDimArray[0]);
        assertArrayEquals(entity.twoDimArray[1], loaded.twoDimArray[1]);
    }

    private static class ContainsListOfList {
        @Id
        private ObjectId id;
        private List<List<String>> strings;
        private List<List<Integer>> integers;
    }

    private static class ContainsTwoDimensionalArray {
        @Id
        private ObjectId id;
        private byte[] oneDimArray;
        private byte[][] twoDimArray;
    }

    @Entity(useDiscriminator = false)
    public static class City {
        @Id
        private ObjectId id;
        private String name;
        private byte[] array;
        private int[][] cells = new int[2][2];
    }

}
