package xyz.morphia.mapping;


import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.ClassModel;
import xyz.morphia.internal.DatastoreImpl;
import xyz.morphia.EntityInterceptor;
import xyz.morphia.Key;
import xyz.morphia.logging.Logger;
import xyz.morphia.logging.MorphiaLoggerFactory;
import xyz.morphia.mapping.codec.DocumentWriter;
import xyz.morphia.mapping.codec.EnumCodecProvider;
import xyz.morphia.mapping.codec.MorphiaCodec;
import xyz.morphia.mapping.codec.MorphiaCodecProvider;
import xyz.morphia.mapping.codec.MorphiaTypesCodecProvider;
import xyz.morphia.mapping.codec.PrimitiveCodecProvider;
import xyz.morphia.utils.ReflectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;


/**
 * This is the heart of Morphia and takes care of mapping from/to POJOs/Documents.  This class is thread-safe and keeps various
 * "cached" data which should speed up processing.  This class should not be referenced outside of Morphia code to types that extend
 * Morphia types.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class Mapper {
    /**
     * The @{@link xyz.morphia.annotations.Id} field name that is stored with mongodb.
     */
    public static final String ID_KEY = "_id";
    /**
     * Special name that can never be used. Used as default for some fields to indicate default state.
     */
    public static final String IGNORED_FIELDNAME = ".";
    /**
     * Special field used by morphia to support various possibly loading issues; will be replaced when discriminators are implemented to
     * support polymorphism
     */
    public static final String CLASS_NAME_FIELDNAME = "className";
    private static final Logger LOG = MorphiaLoggerFactory.get(Mapper.class);
    private final Set<String> restrictedPackages = new HashSet<>(asList("java", "javax", "org.bson"));

    /**
     * Set of classes that registered by this mapper
     */
    private final Map<Class, MappedClass> mappedClasses = new HashMap<>();
    private final Map<String, Set<MappedClass>> mappedClassesByCollection = new ConcurrentHashMap<>();

    //EntityInterceptors; these are called after EntityListeners and lifecycle methods on an Entity, for all Entities
    private final List<EntityInterceptor> interceptors = new LinkedList<>();

    //A general cache of instances of classes; used by MappedClass for EntityListener(s)
    private final Map<Class, Object> instanceCache = new ConcurrentHashMap();
    private final Set<String> packages = new HashSet<>();
    private CodecRegistry codecRegistry;
    private DatastoreImpl datastore;
    private MapperOptions opts;

    /**
     * Creates a new Mapper
     * @param datastore the datastore to use
     * @param codecRegistry the CodedRegistry from the Java driver
     */
    public Mapper(final DatastoreImpl datastore, final CodecRegistry codecRegistry) {
        this(datastore, codecRegistry, new MapperOptions());
    }

    /**
     * Creates a Mapper with the given options.
     *
     * @param datastore the datastore to use
     * @param opts      the options to use
     */
    Mapper(final DatastoreImpl datastore, final CodecRegistry codecRegistry, final MapperOptions opts) {
        this.datastore = datastore;

        this.opts = opts;
        final MorphiaCodecProvider codecProvider = new MorphiaCodecProvider(datastore, this,
            singletonList(new MorphiaConvention(datastore, opts)));
        final MorphiaTypesCodecProvider typesCodecProvider = new MorphiaTypesCodecProvider(this);

        this.codecRegistry = fromRegistries(fromProviders(new MorphiaShortCutProvider(this, codecProvider)),
            new PrimitiveCodecProvider(codecRegistry),
            codecRegistry,
            fromProviders(new EnumCodecProvider(), typesCodecProvider, codecProvider));
    }

    /**
     * @return the codec registry
     */
    public CodecRegistry getCodecRegistry() {
        return codecRegistry;
    }

    /**
     * @return the packages to scan
     */
    public Set<String> getPackages() {
        return packages;
    }

    /**
     * Adds an {@link EntityInterceptor}
     *
     * @param ei the interceptor to add
     */
    public void addInterceptor(final EntityInterceptor ei) {
        interceptors.add(ei);
    }

    /**
     * @return true if {@code EntityInterceptor}s have been defined
     */
    public boolean hasInterceptors() {
        return !interceptors.isEmpty();
    }

    /**
     * Finds any subtypes for the given MappedClass.
     *
     * @param mc the parent type
     * @return the list of subtypes
     * @since 1.3
     */
    public List<MappedClass> getSubTypes(final MappedClass mc) {
        List<MappedClass> subtypes = new ArrayList<>();
        for (MappedClass mappedClass : getMappedClasses()) {
            if (mappedClass.isSubType(mc)) {
                subtypes.add(mappedClass);
            }
        }

        return subtypes;
    }

    /**
     * @return collection of MappedClasses
     */
    public Collection<MappedClass> getMappedClasses() {
        return new ArrayList<>(mappedClasses.values());
    }

    /**
     * Given a collection name, return the Class of the type mapped to that collection
     * @param collection the name of the collection
     * @param <T> type mapped type
     *
     * @return the class definiton
     */
    public <T> Class<T> getClassFromCollection(final String collection) {
        final Set<MappedClass> mcs = mappedClassesByCollection.get(collection);
        if (mcs == null || mcs.isEmpty()) {
            throw new MappingException(format("The collection '%s' is not mapped to a java class.", collection));
        }
        if (mcs.size() > 1) {
            if (LOG.isInfoEnabled()) {
                LOG.info(format("Found more than one class mapped to collection '%s'%s", collection, mcs));
            }
        }
        return mcs.stream()
                  .findFirst()
                  .map(mc -> (Class<T>) mc.getClazz())
                  .orElse(null);
    }

    /**
     * Given a collection name, return the MappedClass of the types mapped to that collection
     * @param collection the name of the collection
     *
     * @return the MappedClasses
     */
    public List<MappedClass> getClassesMappedToCollection(final String collection) {
        final Set<MappedClass> mcs = mappedClassesByCollection.get(collection);
        if (mcs == null || mcs.isEmpty()) {
            throw new MappingException(format("The collection '%s' is not mapped to a java class.", collection));
        }
        return new ArrayList<>(mcs);
    }

    /**
     * Gets the mapped collection for an object instance or Class reference.
     *
     * @param classModel the classModel to process
     * @return the collection name
     */
    public String getCollectionName(final ClassModel classModel) {
        if (classModel == null) {
            throw new IllegalArgumentException();
        }

        return getCollectionName(classModel.getType());
    }

    /**
     * @param type the type to look up
     * @return the mapped collection name
     */
    public String getCollectionName(final Class type) {
        final MappedClass mc = getMappedClass(type);
        if (mc == null) {
            throw new MappingException(format("%s is not a mapped type", type.getName()));
        }

        return mc.getCollectionName();
    }

    /**
     * Gets the {@link MappedClass} for the object (type). If it isn't mapped, create a new class and cache it (without validating).
     *
     * @param obj the object to process
     * @return the MappedClass for the object given
     */
    public MappedClass getMappedClass(final Object obj) {
        if (obj == null) {
            return null;
        }

        Class type = (obj instanceof Class) ? (Class) obj : obj.getClass();

        MappedClass mc = null;
        if (isMappable(type)) {

            mc = mappedClasses.get(type);
            if (mc == null) {
                mc = addMappedClass(type);
            }
        }
        return mc;
    }

    /**
     * @param clazz the class of the type to inspect
     * @param <T> the type defintion
     * @return true if the given class is mappable by Morphia
     */
    public <T> boolean isMappable(final Class<T> clazz) {
        if (clazz.isEnum() || clazz.getPackage() == null) {
            return false;
        }
        for (String restrictedPackage : restrictedPackages) {
            if (clazz.getPackage().getName().startsWith(restrictedPackage + ".")) {
                return false;
            }
        }

        return clazz.getPackage() != null && (packages.isEmpty() || packages.contains(clazz.getPackage().getName()));
    }

    /**
     * Creates a MappedClass and validates it.
     *
     * @param c the Class to map
     * @return the MappedClass for the given Class
     */
    public MappedClass addMappedClass(final Class c) {
        MappedClass mappedClass = mappedClasses.get(c);
        if (mappedClass == null) {
            //            try {
            final Codec codec1 = codecRegistry.get(c);
            if (codec1 instanceof MorphiaCodec) {
                return addMappedClass(((MorphiaCodec) codec1).getMappedClass());
            }
            //            } catch (CodecConfigurationException e) {
            //                LOG.error(e.getMessage(), e);
            //                return null;
            //            }
        }
        return mappedClass;
    }

    private MappedClass addMappedClass(final MappedClass mc) {
        if (!mc.isInterface()) {
            mc.validate(this);
        }

        mappedClasses.put(mc.getClazz(), mc);
        mappedClassesByCollection.computeIfAbsent(mc.getCollectionName(), s -> new CopyOnWriteArraySet<>())
                                 .add(mc);

        return mc;
    }

    /**
     * @return the options used by this Mapper
     */
    public MapperOptions getOptions() {
        return opts;
    }

    /**
     * Sets the options this Mapper should use
     *
     * @param options the options to use
     */
    public void setOptions(final MapperOptions options) {
        opts = options;
    }

    /**
     * @return the cache of instances
     */
    Map<Class, Object> getInstanceCache() {
        return instanceCache;
    }

    /**
     * Gets list of {@link EntityInterceptor}s
     *
     * @return the Interceptors
     */
    Collection<EntityInterceptor> getInterceptors() {
        return interceptors;
    }

    /**
     * Gets the Key for an entity
     *
     * @param entity the entity to process
     * @param <T>    the type of the entity
     * @return the Key
     */
    public <T> Key<T> getKey(final T entity) {
        if (entity instanceof Key) {
            return (Key<T>) entity;
        }

        final Object id = getId(entity);
        final Class<T> aClass = (Class<T>) entity.getClass();
        return id == null ? null : new Key<>(aClass, getCollectionName(aClass), id);
    }

    /**
     * Gets the Key for an entity and a specific collection
     *
     * @param entity     the entity to process
     * @param collection the collection to use in the Key rather than the mapped collection as defined on the entity's class
     * @param <T>        the type of the entity
     * @return the Key
     */
    public <T> Key<T> getKey(final T entity, final String collection) {
        if (entity instanceof Key) {
            return (Key<T>) entity;
        }

        final Object id = getId(entity);
        final Class<T> aClass = (Class<T>) entity.getClass();
        return id == null ? null : new Key<>(aClass, collection, id);
    }

    /**
     * Gets the ID value for an entity
     *
     * @param entity the entity to process
     * @return the ID value
     */
    public Object getId(final Object entity) {
        if (entity == null) {
            return null;
        }
        try {
            final MappedClass mappedClass = getMappedClass(entity.getClass());
            final MappedField idField = mappedClass.getIdField();
            return idField.getFieldValue(entity);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Maps a set of classes
     *
     * @param entityClasses the classes to map
     */
    public void map(final Class... entityClasses) {
        for (final Class entityClass : entityClasses) {
            getMappedClass(entityClass);
        }
    }

    /**
     * Maps all the classes found in the package to which the given class belongs.
     *
     * @param clazz the class to use when trying to find others to map
     */
    public void mapPackageFromClass(final Class clazz) {
        mapPackage(clazz.getPackage().getName());
    }

    /**
     * Tries to map all classes in the package specified. Fails if one of the classes is not valid for mapping.
     *
     * @param packageName the name of the package to process
     */
    public void mapPackage(final String packageName) {
        map(ReflectionUtils.getClasses(packageName, opts.isMapSubPackages()));
    }

    /**
     * Maps a set of classes
     *
     * @param entityClasses the classes to map
     */
    public void map(final Set<Class> entityClasses) {
        if (entityClasses != null && !entityClasses.isEmpty()) {
            for (final Class entityClass : entityClasses) {
                getMappedClass(entityClass);
            }
        }
    }

    /**
     * Converts an entity to a {@code Document}.  This is an internal Morphia method and it may change without notice.
     *
     * @param entity the entity to map
     * @param <T> the type of the entity
     * @return the Document
     */
    public <T> Document toDocument(final T entity) {
        final Class<T> aClass = (Class<T>) entity.getClass();
        final DocumentWriter writer = new DocumentWriter();
        codecRegistry.get(aClass).encode(writer, entity, EncoderContext.builder().build());

        return writer.getRoot();
    }

    /**
     * @param clazz the class to inspect
     * @param <T> the type
     * @return true if the type has been Mapped
     */
    public <T> boolean isMapped(final Class<T> clazz) {
        return mappedClasses.get(clazz) != null;
    }

    /**
     * Determines if the type of an instance is mappable by Morphia.
     *
     * @param value the value to inspect
     * @return true if the type is mappable
     */
    public boolean isMappable(final Object value) {
        boolean mappable;
        if (value == null) {
            mappable = false;
        } else if (value instanceof Iterable) {
            Iterator iterator = ((Iterable) value).iterator();
            mappable = iterator.hasNext() && isMappable(iterator.next());
        } else {
            mappable = isMappable(value.getClass());
        }
        return mappable;
    }

}
