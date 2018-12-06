package xyz.morphia;


import org.bson.codecs.Codec;
import org.bson.codecs.EncoderContext;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.PostLoad;
import xyz.morphia.annotations.Property;
import xyz.morphia.annotations.Reference;
import xyz.morphia.mapping.EmbeddedMappingTest.AnotherNested;
import xyz.morphia.mapping.EmbeddedMappingTest.Nested;
import xyz.morphia.mapping.EmbeddedMappingTest.NestedImpl;
import xyz.morphia.mapping.MappedClass;
import xyz.morphia.mapping.Mapper;
import xyz.morphia.mapping.codec.DocumentWriter;

import java.util.List;

import static java.util.Arrays.asList;


/**
 * Tests mapper functions; this is tied to some of the internals.
 */
public class TestMapper extends TestBase {
    @Test
    public void serializableId() {
        final CustomId cId = new CustomId();
        cId.id = new ObjectId();
        cId.type = "banker";

        final UsesCustomIdObject object = new UsesCustomIdObject();
        object.id = cId;
        object.text = "hllo";

        final Codec<UsesCustomIdObject> codec = getCodecRegistry().get(UsesCustomIdObject.class);
        final DocumentWriter writer = new DocumentWriter();
        codec.encode(writer, object, EncoderContext.builder().build());

        getDatastore().save(object);
    }

    @Test
    public void singleLookup() {
        getMapper().map(A.class);

        final MappedClass mappedClass = getMapper().getMappedClass(A.class);
        Assert.assertNotNull(mappedClass.getIdField());
        Assert.assertNotNull(mappedClass.getMappedIdField());

        A.loadCount = 0;
        final A a = new A();
        HoldsMultipleA holder = new HoldsMultipleA();
        holder.a1 = a;
        holder.a2 = a;
        getDatastore().save(asList(a, holder));
        holder = getDatastore().find(HoldsMultipleA.class).filter("_id", holder.id).first();
        Assert.assertEquals(holder.a1, holder.a2);
        Assert.assertEquals(1, A.loadCount);
    }

    @Test
    public void subTypes() {
        getMapper().map(NestedImpl.class, AnotherNested.class);

        Mapper mapper = getMapper();
        List<MappedClass> subTypes = mapper.getSubTypes(mapper.getMappedClass(Nested.class));
        Assert.assertFalse(subTypes.isEmpty());
        Assert.assertTrue(subTypes.contains(mapper.getMappedClass(NestedImpl.class)));
        Assert.assertTrue(subTypes.contains(mapper.getMappedClass(AnotherNested.class)));
    }

    public static class A {
        private static int loadCount;
        @Id
        private ObjectId id;

        @PostLoad
        protected void postConstruct() {
            if (loadCount > 1) {
                throw new RuntimeException("PostLoad called more than once");
            }

            loadCount++;
        }

        String getId() {
            return id.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof A)) {
                return false;
            }

            final A a = (A) o;

            return id != null ? id.equals(a.id) : a.id == null;
        }

        @Override
        public int hashCode() {
            return id != null ? id.hashCode() : 0;
        }
    }

    @Entity("holders")
    public static class HoldsMultipleA {
        @Id
        private ObjectId id;
        @Reference
        private A a1;
        @Reference
        private A a2;
    }

    public static class CustomId {

        @Property("v")
        private ObjectId id;
        @Property("t")
        private String type;

        public ObjectId getId() {
            return id;
        }

        public void setId(final ObjectId id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(final String type) {
            this.type = type;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            result = prime * result + ((type == null) ? 0 : type.hashCode());
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof CustomId)) {
                return false;
            }
            final CustomId other = (CustomId) obj;
            if (id == null) {
                if (other.id != null) {
                    return false;
                }
            } else if (!id.equals(other.id)) {
                return false;
            }
            if (type == null) {
                if (other.type != null) {
                    return false;
                }
            } else if (!type.equals(other.type)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("CustomId [");
            if (id != null) {
                builder.append("id=").append(id).append(", ");
            }
            if (type != null) {
                builder.append("type=").append(type);
            }
            builder.append("]");
            return builder.toString();
        }
    }

    public static class UsesCustomIdObject {
        @Id
        private CustomId id;
        private String text;

        public CustomId getId() {
            return id;
        }

        public void setId(final CustomId id) {
            this.id = id;
        }

        public String getText() {
            return text;
        }

        public void setText(final String text) {
            this.text = text;
        }
    }

}
