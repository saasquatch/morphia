package xyz.morphia.query;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import xyz.morphia.Key;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Reference;
import xyz.morphia.annotations.Serialized;
import xyz.morphia.entities.EntityWithListsAndArrays;
import xyz.morphia.entities.SimpleEntity;
import xyz.morphia.mapping.MappedClass;
import xyz.morphia.mapping.MappedField;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static xyz.morphia.query.FilterOperator.ALL;
import static xyz.morphia.query.FilterOperator.EQUAL;
import static xyz.morphia.query.FilterOperator.EXISTS;
import static xyz.morphia.query.FilterOperator.GEO_WITHIN;
import static xyz.morphia.query.FilterOperator.IN;
import static xyz.morphia.query.FilterOperator.MOD;
import static xyz.morphia.query.FilterOperator.NOT_IN;
import static xyz.morphia.query.FilterOperator.SIZE;
import static xyz.morphia.query.QueryValidator.isCompatibleForOperator;
import static xyz.morphia.query.QueryValidator.validateQuery;

public class QueryValidatorTest extends TestBase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void shouldAllowAllOperatorForIterableMapAndArrayValues() {
        assertTrue(isCompatibleForOperator(null, null, SimpleEntity.class, ALL, Arrays.asList(1, 2),
            new ArrayList<>()));
        assertTrue(isCompatibleForOperator(null, null, SimpleEntity.class, ALL, Collections.emptySet(),
            new ArrayList<>()));
        assertTrue(isCompatibleForOperator(null, null, SimpleEntity.class, ALL, new HashMap<String, String>(),
            new ArrayList<>()));
        assertTrue(isCompatibleForOperator(null, null, SimpleEntity.class, ALL, new int[0],
            new ArrayList<>()));
    }

    @Test
    public void shouldAllowBooleanValuesForExistsOperator() {
        assertTrue(isCompatibleForOperator(null, null, SimpleEntity.class, EXISTS, true, new ArrayList<>()));
    }

    @Test
//    @Ignore("Defer fixing the geo tests until after the core is fixed")
    public void shouldAllowGeoWithinOperatorWithAllAppropriateTrimmings() {
        MappedClass mappedClass = getMapper().getMappedClass(GeoEntity.class);
        MappedField mappedField = mappedClass.getMappedField("array");
        assertTrue(isCompatibleForOperator(mappedClass, mappedField, List.class, GEO_WITHIN, new Document("$box", 1),
            new ArrayList<>()));
    }

    @Test
    public void shouldAllowInOperatorForIterableMapAndArrayValues() {
        assertTrue(isCompatibleForOperator(null, null, SimpleEntity.class, IN, Arrays.asList(1, 2),
            new ArrayList<>()));
        assertTrue(isCompatibleForOperator(null, null, SimpleEntity.class, IN, Collections.emptySet(),
            new ArrayList<>()));
        assertTrue(isCompatibleForOperator(null, null, SimpleEntity.class, IN, new HashMap<String, String>(),
            new ArrayList<>()));
        assertTrue(isCompatibleForOperator(null, null, SimpleEntity.class, IN, new int[0], new ArrayList<>()));
    }

    @Test
    public void shouldAllowModOperatorForArrayOfIntegerValues() {
        assertTrue(isCompatibleForOperator(null, null, SimpleEntity.class, MOD, new int[2], new ArrayList<>()));
    }

    @Test
    public void shouldAllowNotInOperatorForIterableMapAndArrayValues() {
        assertTrue(isCompatibleForOperator(null, null, SimpleEntity.class, NOT_IN, Arrays.asList(1, 2),
            new ArrayList<>()));
        assertTrue(isCompatibleForOperator(null, null, SimpleEntity.class, NOT_IN, Collections.emptySet(),
            new ArrayList<>()));
        assertTrue(isCompatibleForOperator(null, null, SimpleEntity.class, NOT_IN, new HashMap<String, String>(),
            new ArrayList<>()));
        assertTrue(isCompatibleForOperator(null, null, SimpleEntity.class, NOT_IN, new int[0], new ArrayList<>()));
    }

    @Test
    public void shouldAllowSizeOperatorForArrayListTypesAndIntegerValues() {
        MappedClass mappedClass = getMapper().getMappedClass(EntityWithListsAndArrays.class);
        MappedField mappedField = mappedClass.getMappedField("listOfIntegers");

        assertTrue(isCompatibleForOperator(mappedClass, mappedField, NullClass.class, SIZE, 3, new ArrayList<>()));
    }

    @Test
    public void shouldAllowSizeOperatorForArraysAndIntegerValues() {
        MappedClass mappedClass = getMapper().getMappedClass(EntityWithListsAndArrays.class);
        MappedField mappedField = mappedClass.getMappedField("arrayOfInts");

        assertTrue(isCompatibleForOperator(mappedClass, mappedField, NullClass.class, SIZE, 3, new ArrayList<>()));
    }

    @Test
    public void shouldAllowSizeOperatorForListTypesAndIntegerValues() {
        MappedClass mappedClass = getMapper().getMappedClass(EntityWithListsAndArrays.class);
        MappedField mappedField = mappedClass.getMappedField("listOfIntegers");

        assertTrue(isCompatibleForOperator(mappedClass, mappedField, NullClass.class, SIZE, 3, new ArrayList<>()));
    }

    @Test
    public void shouldAllowTypeThatMatchesKeyTypeValue() {
        MappedClass mappedClass = getMapper().getMappedClass(SimpleEntity.class);
        MappedField mappedField = mappedClass.getMappedField("integer");
        assertTrue(isCompatibleForOperator(mappedClass, mappedField, Integer.class, EQUAL,
            new Key<Number>(Integer.class, "Integer", new ObjectId()), new ArrayList<>()));
    }

    @Test
    public void shouldAllowValueOfPatternWithTypeOfString() {
        assertTrue(isCompatibleForOperator(null, null, String.class, EQUAL, Pattern.compile("."),
            new ArrayList<>()));
    }

    @Test
    public void shouldAllowValueWithEntityAnnotationAndTypeOfKey() {
        assertTrue(isCompatibleForOperator(null, null, Key.class, EQUAL, new SimpleEntity(), new ArrayList<>()));
    }

    @Test
    public void shouldAllowValuesOfIntegerIfTypeIsDouble() {
        assertTrue(isCompatibleForOperator(null, null, Double.class, EQUAL, 1, new ArrayList<>()));
        assertTrue(isCompatibleForOperator(null, null, double.class, EQUAL, 1, new ArrayList<>()));
    }

    @Test
    public void shouldAllowValuesOfIntegerIfTypeIsInteger() {
        assertTrue(isCompatibleForOperator(null, null, int.class, EQUAL, 1, new ArrayList<>()));
        assertTrue(isCompatibleForOperator(null, null, Integer.class, EQUAL, 1, new ArrayList<>()));
    }

    @Test
    public void shouldAllowValuesOfIntegerOrLongIfTypeIsLong() {
        assertTrue(isCompatibleForOperator(null, null, Long.class, EQUAL, 1, new ArrayList<>()));
        assertTrue(isCompatibleForOperator(null, null, long.class, EQUAL, 1, new ArrayList<>()));
        assertTrue(isCompatibleForOperator(null, null, Long.class, EQUAL, 1L, new ArrayList<>()));
        assertTrue(isCompatibleForOperator(null, null, long.class, EQUAL, 1L, new ArrayList<>()));
    }

    @Test
    public void shouldAllowValuesOfList() {
        // expect
        MappedClass mappedClass = getMapper().getMappedClass(SimpleEntity.class);
        MappedField mappedField = mappedClass.getMappedField("name");
        assertTrue(isCompatibleForOperator(mappedClass, mappedField, List.class, EQUAL, new ArrayList<String>(),
            new ArrayList<>()));
    }

    @Test
    public void shouldAllowValuesOfLongIfTypeIsDouble() {
        assertTrue(isCompatibleForOperator(null, null, Double.class, EQUAL, 1L, new ArrayList<>()));
        assertTrue(isCompatibleForOperator(null, null, double.class, EQUAL, 1L, new ArrayList<>()));
    }

    @Test
    public void shouldBeCompatibleIfTypeIsNull() {
        // expect
        // frankly not sure we should just let nulls through
        assertTrue(isCompatibleForOperator(null, null, null, EQUAL, "value", new ArrayList<>()));
    }

    @Test
    public void shouldBeCompatibleIfValueIsNull() {
        // frankly not sure we should just let nulls through
        assertTrue(isCompatibleForOperator(null, null, SimpleEntity.class, EQUAL, null, new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowGeoOperatorIfValueDoesNotContainCorrectField() {
        MappedClass mappedClass = getMapper().getMappedClass(GeoEntity.class);
        MappedField mappedField = mappedClass.getMappedField("array");
        assertFalse(isCompatibleForOperator(mappedClass, mappedField, List.class, GEO_WITHIN,
            new Document("name", "value"), new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowGeoOperatorIfValueIsNotDocument() {
        MappedClass mappedClass = getMapper().getMappedClass(GeoEntity.class);
        MappedField mappedField = mappedClass.getMappedField("array");
        assertFalse(isCompatibleForOperator(mappedClass, mappedField, List.class, GEO_WITHIN, "value",
            new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowGeoWithinWhenValueDoesNotContainKeyword() {
        MappedClass mappedClass = getMapper().getMappedClass(GeoEntity.class);
        MappedField mappedField = mappedClass.getMappedField("array");
        assertFalse(isCompatibleForOperator(mappedClass, mappedField, List.class, GEO_WITHIN,
            new Document("notValidKey", 1), new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowModOperatorWithNonArrayValue() {
        assertFalse(isCompatibleForOperator(null, null, String.class, MOD, "value", new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowModOperatorWithNonIntegerArray() {
        assertFalse(isCompatibleForOperator(null, null, SimpleEntity.class, MOD, new String[]{"value"},
            new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowNonBooleanValuesForExistsOperator() {
        MappedClass mappedClass = getMapper().getMappedClass(SimpleEntity.class);
        MappedField mappedField = mappedClass.getMappedField("name");
        assertFalse(isCompatibleForOperator(mappedClass, mappedField, SimpleEntity.class, EXISTS, "value",
            new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowNonIntegerTypeIfValueIsInt() {
        MappedClass mappedClass = getMapper().getMappedClass(SimpleEntity.class);
        MappedField mappedField = mappedClass.getMappedField("name");
        assertFalse(isCompatibleForOperator(mappedClass, mappedField, SimpleEntity.class, EQUAL, 1,
            new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowNonIntegerValueIfTypeIsInt() {
        assertFalse(isCompatibleForOperator(null, null, int.class, EQUAL, "some non int value",
            new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowNonKeyTypeWithKeyValue() {
        MappedClass mappedClass = getMapper().getMappedClass(EntityWithListsAndArrays.class);
        MappedField mappedField = mappedClass.getMappedField("listOfIntegers");
        assertFalse(isCompatibleForOperator(mappedClass, mappedField, SimpleEntity.class, EQUAL,
            new Key<>(String.class, "collection", new ObjectId()), new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowNonStringTypeWithValueOfPattern() {
        assertFalse(isCompatibleForOperator(null, null, Pattern.class, EQUAL, Pattern.compile("."),
            new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowOtherValuesForAllOperator() {
        MappedClass mappedClass = getMapper().getMappedClass(SimpleEntity.class);
        MappedField mappedField = mappedClass.getMappedField("name");

        assertFalse(isCompatibleForOperator(mappedClass, mappedField, SimpleEntity.class, ALL, "value",
            new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowOtherValuesForInOperator() {
        MappedClass mappedClass = getMapper().getMappedClass(SimpleEntity.class);
        MappedField mappedField = mappedClass.getMappedField("name");
        assertFalse(isCompatibleForOperator(mappedClass, mappedField, String.class, IN, "value", new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowOtherValuesForNotInOperator() {
        MappedClass mappedClass = getMapper().getMappedClass(SimpleEntity.class);
        MappedField mappedField = mappedClass.getMappedField("name");
        assertFalse(isCompatibleForOperator(mappedClass, mappedField, SimpleEntity.class, NOT_IN, "value",
            new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowSizeOperatorForNonIntegerValues() {
        MappedClass mappedClass = getMapper().getMappedClass(SimpleEntity.class);
        MappedField mappedField = mappedClass.getMappedField("name");
        assertFalse(isCompatibleForOperator(mappedClass, mappedField, ArrayList.class, SIZE, "value", new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowSizeOperatorForNonListTypes() {
        MappedClass mappedClass = getMapper().getMappedClass(EntityWithListsAndArrays.class);
        MappedField mappedField = mappedClass.getMappedField("notAnArrayOrList");

        assertFalse(isCompatibleForOperator(mappedClass, mappedField, NullClass.class, SIZE, 3, new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowStringValueWithTypeThatIsNotString() {
        MappedClass mappedClass = getMapper().getMappedClass(SimpleEntity.class);
        MappedField mappedField = mappedClass.getMappedField("name");
        assertFalse(isCompatibleForOperator(mappedClass, mappedField, Integer.class, EQUAL, "value", new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowTypeThatDoesNotMatchKeyTypeValue() {
        MappedClass mappedClass = getMapper().getMappedClass(SimpleEntity.class);
        MappedField mappedField = mappedClass.getMappedField("name");
        assertFalse(isCompatibleForOperator(mappedClass, mappedField, String.class, EQUAL,
            new Key<Number>(Integer.class, "Integer", new ObjectId()),
            new ArrayList<>()));
    }

    @Test
    public void shouldNotAllowValueWithoutEntityAnnotationAndTypeOfKey() {
        MappedClass mappedClass = getMapper().getMappedClass(SimpleEntity.class);
        MappedField mappedField = mappedClass.getMappedField("name");
        assertFalse(isCompatibleForOperator(mappedClass, mappedField, Key.class, EQUAL, "value", new ArrayList<>()));
    }

    @Test
    public void shouldNotErrorIfModOperatorIsUsedWithZeroLengthArrayOfIntegerValues() {
        assertFalse(isCompatibleForOperator(null, null, SimpleEntity.class, MOD, new int[0], new ArrayList<>()));
    }

    @Test
    public void shouldNotErrorModOperatorWithArrayOfNullValues() {
        assertFalse(isCompatibleForOperator(null, null, SimpleEntity.class, MOD, new String[1], new ArrayList<>()));
    }

    @Test
    public void shouldNotErrorWhenValidateQueryCalledWithNullValue() {
        // this unit test is to drive fixing a null pointer in the logging code.  It's a bit stupid but it's an edge case that wasn't
        // caught. when this is called, don't error
        validateQuery(SimpleEntity.class, getMapper(), new StringBuilder("name"), EQUAL, null, true, true);
    }

    @Test
    public void shouldRejectNonDoubleValuesIfTypeIsDouble() {
        assertFalse(isCompatibleForOperator(null, null, Double.class, EQUAL, "Not a double",
            new ArrayList<>()));
    }

    @Test
    public void shouldRejectTypesAndValuesThatDoNotMatch() {
        MappedClass mappedClass = getMapper().getMappedClass(SimpleEntity.class);
        MappedField mappedField = mappedClass.getMappedField("name");
        assertFalse(isCompatibleForOperator(mappedClass, mappedField, String.class, EQUAL, 1,
            new ArrayList<>()));
    }

    @Test(expected = ValidationException.class)
    public void shouldReferToMappedClassInExceptionWhenFieldNotFound() {
        validateQuery(SimpleEntity.class, getMapper(), new StringBuilder("id.notAField"), FilterOperator.EQUAL, 1, true, true);
    }

    @Test
    public void shouldReferToMappedClassInExceptionWhenQueryingPastReferenceField() {
        thrown.expect(ValidationException.class);
        thrown.expectMessage("Cannot use dot-notation past 'reference' in 'xyz.morphia.query.QueryValidatorTest$WithReference'");
        validateQuery(WithReference.class, getMapper(), new StringBuilder("reference.name"), FilterOperator.EQUAL, "", true,
            true);
    }

    private static class GeoEntity {
        private final int[] array = {1};
    }

    private static class NullClass {
    }

    private static class WithReference {
        @Reference
        private SimpleEntity reference;
    }

    private static class SerializableClass {
        private String name;
    }

    private static class WithSerializedField {
        @Serialized
        private SerializableClass serialized;
    }
}
