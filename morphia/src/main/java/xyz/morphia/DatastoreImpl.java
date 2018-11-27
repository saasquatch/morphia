package xyz.morphia;

import com.mongodb.MongoClient;
import com.mongodb.MongoCommandException;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.DeleteOptions;
import com.mongodb.client.model.FindOneAndDeleteOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.FindOptions;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.InsertOneOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.ValidationOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.Document;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.ClassModel;
import xyz.morphia.aggregation.AggregationPipeline;
import xyz.morphia.aggregation.AggregationPipelineImpl;
import xyz.morphia.annotations.CappedAt;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Validation;
import xyz.morphia.annotations.Version;
import xyz.morphia.logging.Logger;
import xyz.morphia.logging.MorphiaLoggerFactory;
import xyz.morphia.mapping.MappedClass;
import xyz.morphia.mapping.MappedField;
import xyz.morphia.mapping.Mapper;
import xyz.morphia.mapping.MappingException;
import xyz.morphia.query.DefaultQueryFactory;
import xyz.morphia.query.Query;
import xyz.morphia.query.QueryException;
import xyz.morphia.query.QueryFactory;
import xyz.morphia.query.UpdateException;
import xyz.morphia.query.UpdateOperations;
import xyz.morphia.query.UpdateOpsImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.bson.Document.parse;

/**
 * Direct use of this class is likely to break in the future.  Do not use.
 */
@SuppressWarnings({ "unchecked"})
public class DatastoreImpl implements AdvancedDatastore {
    private static final Logger LOG = MorphiaLoggerFactory.get(DatastoreImpl.class);

    private final MongoDatabase database;
    private final IndexHelper indexHelper;
    private Mapper mapper;
    private WriteConcern defaultWriteConcern;

    private volatile QueryFactory queryFactory = new DefaultQueryFactory();

    /**
     * Create a new DatastoreImpl
     *
     * @param mongoClient the connection to the MongoDB instance
     * @param dbName      the name of the database for this data store.
     */
    DatastoreImpl(final MongoClient mongoClient, final String dbName) {
        this.mapper = new Mapper(this, mongoClient.getMongoClientOptions().getCodecRegistry());
        this.defaultWriteConcern = mongoClient.getWriteConcern();

        this.database = mongoClient.getDatabase(dbName)
                                   .withCodecRegistry(mapper.getCodecRegistry());

        this.indexHelper = new IndexHelper(this.mapper, database);
    }

    /**
     * This is an internal method.  Not for use outside of Morphia
     * @return the CodedRegistry used here
     */
    public CodecRegistry getCodecRegistry() {
        return mapper.getCodecRegistry();
    }

    @Override
    public AggregationPipeline createAggregation(final Class source) {
        return new AggregationPipelineImpl(this, getCollection(source));
    }

    @Override
    public <T> Query<T> createQuery(final Class<T> collection) {
        return newQuery(collection, getCollection(collection));
    }

    @Override
    public <T> UpdateOperations<T> createUpdateOperations(final Class<T> clazz) {
        return new UpdateOpsImpl<>(clazz, getMapper());
    }

    @Override
    public <T, V> DeleteResult deleteOne(final Class<T> clazz, final V id) {
        return deleteOne(clazz, id, new DeleteOptions(), enforceWriteConcern(clazz));
    }

    @Override
    public <T, V> DeleteResult deleteOne(final String collectionName, final Class<T> clazz, final V id) {
        return deleteOne(collectionName, clazz, id, new DeleteOptions(), enforceWriteConcern(clazz));
    }

    @Override
    public <T, V> DeleteResult deleteOne(final Class<T> clazz, final V id, final DeleteOptions options, final WriteConcern writeConcern) {
        return deleteMany(createQuery(clazz).filter(Mapper.ID_KEY, id), options, writeConcern);
    }

    @Override
    public <T, V> DeleteResult deleteOne(final String collectionName, final Class<T> clazz, final V id, final DeleteOptions options,
                                      final WriteConcern writeConcern) {
        return deleteMany(createQuery(collectionName, clazz).filter(Mapper.ID_KEY, id), options, writeConcern);
    }

    @Override
    public <T, V> DeleteResult deleteMany(final Class<T> clazz, final List<V> ids) {
        return deleteMany(find(clazz).filter(Mapper.ID_KEY + " in", ids));
    }

    @Override
    public <T, V> DeleteResult deleteMany(final String collectionName, final Class<T> clazz, final List<V> ids) {
        return deleteMany(find(collectionName, clazz).filter(Mapper.ID_KEY + " in", ids));
    }

    @Override
    public <T, V> DeleteResult deleteMany(final Class<T> clazz, final List<V> ids, final DeleteOptions options,
                                          final WriteConcern writeConcern) {
        return deleteMany(find(clazz).filter(Mapper.ID_KEY + " in", ids), options, writeConcern);
    }

    @Override
    public <T, V> DeleteResult deleteMany(final String collectionName,
                                      final Class<T> clazz,
                                      final List<V> ids,
                                      final DeleteOptions options,
                                      final WriteConcern writeConcern) {
        return deleteMany(find(collectionName, clazz).filter(Mapper.ID_KEY + " in", ids), options, writeConcern);
    }

    @Override
    public <T> DeleteResult deleteMany(final Query<T> query) {
        return deleteMany(query, new DeleteOptions(), enforceWriteConcern(query.getEntityClass()));
    }

    @Override
    public <T> DeleteResult deleteMany(final Query<T> query, final DeleteOptions options, final WriteConcern writeConcern) {

        MongoCollection<T> collection = query.getCollection();
        // TODO remove this after testing.
        if (collection == null) {
            collection = getCollection(query.getEntityClass());
        }

        return collection.withWriteConcern(writeConcern)
                         .deleteMany(query.getQueryDocument(), options);
    }

    @Override
    public <T> DeleteResult deleteOne(final T entity) {
        return deleteOne(entity, new DeleteOptions(), enforceWriteConcern(entity.getClass()));
    }

    /**
     * Deletes the given entity (by @Id), with the WriteConcern
     *
     * @param entity  the entity to delete
     * @param options the options to use when deleting
     * @return results of the delete
     */
    @Override
    public <T> DeleteResult deleteOne(final T entity, final DeleteOptions options, final WriteConcern writeConcern) {
        return deleteOne(entity.getClass(), mapper.getId(entity), options, writeConcern);
    }

    @Override
    public void ensureCaps() {

        for (final ClassModel mc : getClassModels()) {
            final Entity annotation = (Entity) mc.getType().getAnnotation(Entity.class);
            if (annotation != null && annotation.cap().value() > 0) {
                final CappedAt cap = annotation.cap();
                final String collName = mapper.getCollectionName(mc);
                final MongoDatabase database = getDatabase();
                if (database.listCollectionNames().into(new ArrayList<>()).contains(collName)) {
                    final Document dbResult = database.runCommand(new Document("collstats", collName));
                    if (dbResult.containsValue("capped")) {
                        LOG.debug("Collection already exists and is capped already; doing nothing. " + dbResult);
                    } else {
                        LOG.warning(format("Collection already exists with same name (%s) and is not capped; not creating capped version!",
                            collName));
                    }
                } else {
                    final CreateCollectionOptions options = new CreateCollectionOptions();
                    options.capped(true);
                    if (cap.value() > 0) {
                        options.sizeInBytes(cap.value());
                    }
                    if (cap.count() > 0) {
                        options.maxDocuments(cap.count());
                    }
                    getDatabase().createCollection(collName, options);
                    LOG.debug(format("Created capped Collection (%s) with opts %s", collName, options));
                }
            }
        }
    }

    @Override
    public void enableDocumentValidation() {
        for (final MappedClass mc : mapper.getMappedClasses()) {
            process(mc, mc.getAnnotation(Validation.class));
        }
    }

    void process(final MappedClass mc, final Validation validation) {
        if (validation != null) {
            String collectionName = mc.getCollectionName();
            try {
                getDatabase().runCommand(new Document("collMod", collectionName)
                                             .append("validator", parse(validation.value()))
                                             .append("validationLevel", validation.level().getValue())
                                             .append("validationAction", validation.action().getValue()));

            } catch (MongoCommandException mce) {
                ValidationOptions options = new ValidationOptions()
                                                .validator(parse(validation.value()))
                                                .validationLevel(validation.level())
                                                .validationAction(validation.action());
                getDatabase().createCollection(collectionName, new CreateCollectionOptions().validationOptions(options));
            }
        }
    }

    private <T> MongoCollection<T> getMongoCollection(final Class<T> clazz) {
        return getMongoCollection(mapper.getCollectionName(clazz), clazz);
    }

    private <T> MongoCollection<T> getMongoCollection(final String name, final Class<T> clazz) {
        return database.getCollection(name, clazz);
    }

    @Override
    public void ensureIndexes() {
        ensureIndexes(false);
    }

    @Override
    public void ensureIndexes(final boolean background) {
        for (final MappedClass mc : mapper.getMappedClasses()) {
            indexHelper.createIndex(getMongoCollection(mc.getClazz()), mc, background);
        }
    }

    @Override
    public <T> void ensureIndexes(final Class<T> clazz) {
        ensureIndexes(clazz, false);
    }

    @Override
    public <T> void ensureIndexes(final Class<T> clazz, final boolean background) {
        indexHelper.createIndex(getMongoCollection(clazz), mapper.getMappedClass(clazz), background);
    }

    @Override
    public <T> void ensureIndexes(final String collection, final Class<T> clazz, final boolean background) {
        indexHelper.createIndex(getMongoCollection(collection, clazz), mapper.getMappedClass(clazz), background);
    }

    @Override
    public Key<?> exists(final Object entityOrKey) {
        final Query<?> query = buildExistsQuery(entityOrKey);
        return query.getKey();
    }

    @Override
    public <T> Query<T> find(final Class<T> clazz) {
        return createQuery(clazz);
    }

    private <T, V> Query<T> find(final String collection, final Class<T> clazz, final V value) {
        return find(collection, clazz).filter(Mapper.ID_KEY, value).enableValidation();
    }

    @Override
    public <T> T findAndDelete(final Query<T> query) {
        return findAndDelete(query, new FindOneAndDeleteOptions(), enforceWriteConcern(query.getEntityClass()));
    }

    @Override
    public <T> T findAndDelete(final Query<T> query, final FindOneAndDeleteOptions options, final WriteConcern writeConcern) {
        MongoCollection<T> collection = query.getCollection();
        if (collection == null) {
            collection = getCollection(query.getEntityClass());
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Executing findAndModify(" + collection.getNamespace().getCollectionName() + ") with delete ...");
        }

        return collection.findOneAndDelete(query.getQueryDocument(), options);
    }

    @Override
    public <T> T findAndModify(final Query<T> query, final UpdateOperations<T> operations, final FindOneAndUpdateOptions options,
                               final WriteConcern writeConcern) {
        MongoCollection<T> collection = query.getCollection();
        // TODO remove this after testing.
        if (collection == null) {
            collection = getCollection(query.getEntityClass());
        }

        if (LOG.isTraceEnabled()) {
            LOG.info("Executing findAndModify(" + collection.getNamespace().getCollectionName() + ") with update ");
        }

        updateVersion(query, operations);

        return collection.withWriteConcern(writeConcern)
                         .findOneAndUpdate(query.getQueryDocument(), operations.getOperations(),
                             options.sort(query.getSortDocument())
                                    .projection(query.getFields()));
    }

    @Override
    public <T> T findAndModify(final Query<T> query, final UpdateOperations<T> operations) {
        return findAndModify(query, operations, new FindOneAndUpdateOptions()
                                                    .returnDocument(ReturnDocument.AFTER), enforceWriteConcern(query.getEntityClass()));
    }

    private <T> void updateVersion(final Query<T> query, final UpdateOperations<T> operations) {

        final MappedClass mc = mapper.getMappedClass(query.getEntityClass());
        MappedField field = mc.getMappedVersionField();

        if (field != null) {
            operations.inc(field.getNameToStore());
        }

    }

    @Override
    public <T, V> Query<T> get(final Class<T> clazz, final List<V> ids) {
        return find(clazz).disableValidation().filter(Mapper.ID_KEY + " in", ids).enableValidation();
    }

    @Override
    public <T, V> T get(final Class<T> clazz, final V id) {
        return find(getCollection(clazz).getNamespace().getCollectionName(), clazz, id).get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(final T entity) {
        final Object id = mapper.getId(entity);
        if (id == null) {
            throw new MappingException("Could not get id for " + entity.getClass().getName());
        }
        return (T) get(entity.getClass(), id);
    }

    @Override
    public <T> T getByKey(final Class<T> clazz, final Key<T> key) {
        final String collectionName = mapper.getCollectionName(clazz);
        final String keyCollection = key.getCollection();

        Object id = key.getId();
        if (id instanceof Document) {
            ((Document) id).remove(Mapper.CLASS_NAME_FIELDNAME);
        }
        return get(keyCollection != null ? keyCollection : collectionName, clazz, id);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T> List<T> getByKeys(final Class<T> clazz, final List<Key<T>> keys) {

        final Map<String, List<Key>> collections = new HashMap<>();
        final List<T> entities = new ArrayList<>();
        // String clazzKind = (clazz==null) ? null :
        // getMapper().getCollectionName(clazz);
        for (final Key<?> key : keys) {
            mapper.coerceCollection(key);

            // if (clazzKind != null && !key.getKind().equals(clazzKind))
            // throw new IllegalArgumentException("Types are not equal (" +
            // clazz + "!=" + key.getKindClass() +
            // ") for key and method parameter clazz");
            //
            if (collections.containsKey(key.getCollection())) {
                collections.get(key.getCollection()).add(key);
            } else {
                collections.put(key.getCollection(), new ArrayList<>(singletonList((Key) key)));
            }
        }
        for (final Map.Entry<String, List<Key>> entry : collections.entrySet()) {
            final List<Object> objIds = entry.getValue().stream()
                 .map(Key::getId)
                 .collect(toList());

            final List results = find(entry.getKey(), entry.getValue().get(0).getType())
                                     .disableValidation()
                                     .filter("_id in", objIds)
                                     .asList();
            entities.addAll(results);
        }

        // TODO: order them based on the incoming Keys.
        return entities;
    }

    @Override
    public <T> List<T> getByKeys(final List<Key<T>> keys) {
        return getByKeys(null, keys);
    }

    @Override
    public <T> long getCount(final T entity) {
        return getCollection(entity.getClass()).countDocuments();
    }

    @Override
    public <T> long getCount(final Class<T> clazz) {
        return getCollection(clazz).countDocuments();
    }

    @Override
    public <T> long getCount(final Query<T> query) {
        return query.count();
    }

    @Override
    public <T> long getCount(final Query<T> query, final CountOptions options) {
        return query.count(options);
    }

    @Override
    public MongoDatabase getDatabase() {
        return database;
    }

    @Override
    public WriteConcern getDefaultWriteConcern() {
        return defaultWriteConcern;
    }

    @Override
    public void setDefaultWriteConcern(final WriteConcern wc) {
        defaultWriteConcern = wc;
    }

    @Override
    public QueryFactory getQueryFactory() {
        return queryFactory;
    }

    @Override
    public void setQueryFactory(final QueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public <T> void merge(final T entity) {
        merge(entity, new InsertOneOptions(), getWriteConcern(entity));
    }

    @Override
    public <T> void merge(final T entity, final InsertOneOptions options, final WriteConcern writeConcern) {
        if (mapper.getKey(entity) == null) {
            throw new MappingException("Could not get ID for " + entity.getClass().getName());
        }
        Document document = mapper.toDocument(entity);

        final Object idValue = document.remove(Mapper.ID_KEY);

        final MongoCollection<T> collection = getCollection((Class<T>) entity.getClass())
                                                  .withWriteConcern(writeConcern);

        UpdateResult result = tryVersionedUpdate(collection, entity, writeConcern);

        if (result == null) {
            final Query<T> query = (Query<T>) createQuery(entity.getClass()).filter(Mapper.ID_KEY, idValue);
            result = updateOne(query, new Document("$set", document), new UpdateOptions()
                                                                       .upsert(false), enforceWriteConcern(entity.getClass()));
        }

        if (result.getModifiedCount() == 0) {
            throw new UpdateException("Nothing updated");
        }

        document.put(Mapper.ID_KEY, idValue);
        postSaveOperations(Collections.singletonList(entity), false);
    }

    @Override
    public <T> Query<T> queryByExample(final T ex) {
        return queryByExample((MongoCollection<T>) getCollection(ex.getClass()), ex);
    }

    private <T> Query<T> queryByExample(final MongoCollection<T> coll, final T example) {
        return newQuery((Class<T>) example.getClass(), coll, mapper.toDocument(example));
    }

    /**
     * Creates and returns a {@link Query} using the underlying {@link QueryFactory}.
     *
     * @see QueryFactory#createQuery(Datastore, MongoCollection, Class, Document)
     */
    private <T> Query<T> newQuery(final Class<T> type, final MongoCollection<T> collection, final Document query) {
        return getQueryFactory().createQuery(this, collection, type, query);
    }

    private <T> UpdateResult tryVersionedUpdate(final MongoCollection<T> origCollection, final T entity, final WriteConcern writeConcern) {
        final MappedClass mc = mapper.getMappedClass(entity);
        if (mc.getFieldsAnnotatedWith(Version.class).isEmpty()) {
            return null;
        }

        final MappedField mappedIdField = mapper.getMappedClass(entity).getMappedIdField();
        final Object id = mappedIdField.getFieldValue(entity);

        final MappedField version = mc.getMappedVersionField();
        Long oldVersion = (Long) version.getFieldValue(entity);
        final long newVersion = oldVersion == null ? 1 : oldVersion + 1;
        version.setFieldValue(entity, newVersion);

        if (id != null) {

            final MongoCollection<T> mongoCollection = origCollection
                                                           .withWriteConcern(writeConcern);

            version.setFieldValue(entity, newVersion);
            final Document query = newQuery((Class<T>) entity.getClass(), origCollection)
                                       .filter(Mapper.ID_KEY, id)
                                       .filter(version.getNameToStore(), oldVersion).getQueryDocument();

            UpdateResult result = updateEntity(mongoCollection, entity, query, new UpdateOptions().upsert(false));
            if (result != null) {
                if (result.getModifiedCount() != 1 && newVersion != 1) {
                    throw new ConcurrentModificationException(format("Entity of class %s (id='%s',version='%d') was concurrently updated.",
                        entity.getClass().getName(), id, oldVersion));
                }
                return result.getModifiedCount() == 0 ? null : result;
            }
        }

        return null;
    }

    private <T> List<Key<T>> postSaveOperations(final List<T> entities/*, final Map<Object, Document> involvedObjects*/) {
        return postSaveOperations(entities, /*involvedObjects, */true);
    }

    @SuppressWarnings("unchecked")
    private <T> List<Key<T>> postSaveOperations(final List<T> entities, /*final Map<Object, Document> involvedObjects, */
                                                final boolean fetchKeys) {
        List<Key<T>> keys = new ArrayList<>();
        for (final T entity : entities) {

            if (fetchKeys) {
                final Key<T> key = getMapper().getKey(entity);
                if (key == null) {
                    throw new MappingException(format("Missing _id after save on %s", entity.getClass().getName()));
                }
                keys.add(key);
            }
        }

        return keys;
    }

    <T> WriteConcern enforceWriteConcern(final Class<T> klass) {
        final WriteConcern klassConcern = getWriteConcern(klass);
        return klassConcern != null ? klassConcern : getDefaultWriteConcern();
    }

    /**
     * Gets the write concern for entity or returns the default write concern for this datastore
     *
     * @param clazzOrEntity the class or entity to use when looking up the WriteConcern
     */
    private WriteConcern getWriteConcern(final Object clazzOrEntity) {
        WriteConcern wc = defaultWriteConcern;
        if (clazzOrEntity != null) {
            final Entity entityAnn = getMapper().getMappedClass(clazzOrEntity).getEntityAnnotation();
            if (entityAnn != null && entityAnn.concern().length() != 0) {
                wc = WriteConcern.valueOf(entityAnn.concern());
            }
        }

        return wc;
    }

    @Override
    public Mapper getMapper() {
        return mapper;
    }

    /**
     * Sets the Mapper this Datastore uses
     *
     * @param mapper the new Mapper
     */
    public void setMapper(final Mapper mapper) {
        this.mapper = mapper;
    }

    private Query<?> buildExistsQuery(final Object entityOrKey) {
        final Key<?> key = mapper.getKey(entityOrKey);
        final Object id = key.getId();
        if (id == null) {
            throw new MappingException("Could not get id for " + entityOrKey.getClass().getName());
        }

        return find(key.getCollection(), key.getType()).filter(Mapper.ID_KEY, key.getId());
    }

    /**
     * Creates and returns a {@link Query} using the underlying {@link QueryFactory}.
     *
     * @see QueryFactory#createQuery(Datastore, MongoCollection, Class)
     */
    private <T> Query<T> newQuery(final Class<T> type, final MongoCollection<T> collection) {
        return getQueryFactory().createQuery(this, collection, type);
    }

    @SuppressWarnings("unchecked")
    private Collection<ClassModel<?>> getClassModels() {
        return getMapper().getMappedClasses().stream().map(MappedClass::getClassModel)
                          .collect(Collectors.toList());
    }

    @Override
    public AggregationPipeline createAggregation(final String collection, final Class<?> clazz) {
        final MongoCollection<?> coll = getDatabase().getCollection(collection, clazz);
        return new AggregationPipelineImpl(this, coll);
    }

    @Override
    public <T> Query<T> createQuery(final String collection, final Class<T> type) {
        return newQuery(type, getDatabase().getCollection(collection, type));
    }

    @Override
    public <T> Query<T> createQuery(final Class<T> clazz, final Document q) {
        return newQuery(clazz, getCollection(clazz), q);
    }

    @Override
    public <T> Query<T> createQuery(final String collection, final Class<T> type, final Document q) {
        return newQuery(type, getCollection(collection, type), q);
    }

    @Override
    public <T> UpdateOperations<T> createUpdateOperations(final Class<T> type, final Document ops) {
        final UpdateOpsImpl<T> upOps = (UpdateOpsImpl<T>) createUpdateOperations(type);
        upOps.setOperations(ops);
        return upOps;
    }

    @Override
    public Key<?> exists(final Object entityOrKey, final ReadPreference readPreference) {
        Query<?> query = buildExistsQuery(entityOrKey);
        if (readPreference != null) {
            query = query.cloneQuery().setReadPreference(readPreference);
        }
        return query.getKey(new FindOptions());
    }

    @Override
    public <T> Query<T> find(final String collection, final Class<T> clazz) {
        return createQuery(collection, clazz);
    }

    @Override
    public <T, V> T get(final String collection, final Class<T> clazz, final V id) {
        return find(collection, clazz)
                   .filter(Mapper.ID_KEY, id)
                   .get();
    }

    @Override
    public long getCount(final String collection) {
        return getCollection(collection, Document.class).countDocuments();
    }

    @Override
    public <T> Key<T> insertOne(final T entity) {
        return insertOne(entity, new InsertOneOptions(), enforceWriteConcern(entity.getClass()));
    }

    @Override
    public <T> Key<T> insertOne(final T entity, final InsertOneOptions options, final WriteConcern writeConcern) {
        return insertOne(getCollection((Class<T>) entity.getClass()), entity, options, writeConcern);
    }

    @Override
    public <T> Key<T> insertOne(final String collection, final T entity) {
        return insertOne(getCollection(collection, (Class<T>) entity.getClass()), entity,
            new InsertOneOptions(), enforceWriteConcern(entity.getClass()));
    }

    @Override
    public <T> Key<T> insertOne(final String collection, final T entity, final InsertOneOptions options, final WriteConcern writeConcern) {
        return insertOne(this.getCollection(collection, (Class<T>) entity.getClass()), entity, options,
            writeConcern);
    }

    /**
     * Inserts entities in to the database
     *
     * @param entities the entities to insert
     * @param <T>      the type of the entities
     * @return the keys of entities
     */
    @Override
    public <T> List<Key<T>> insertMany(final List<T> entities) {
        if (entities.isEmpty()) {
            return emptyList();
        }
        return insertMany(entities, new InsertManyOptions(), enforceWriteConcern(entities.get(0).getClass()));
    }

    @Override
    public <T> List<Key<T>> insertMany(final List<T> entities, final InsertManyOptions options, final WriteConcern writeConcern) {
        if (entities.isEmpty()) {
            return emptyList();
        }
        final Class<T> first = (Class<T>) entities.get(0).getClass();
        return insertMany(getCollection(first), entities, options, writeConcern);
    }

    @Override
    public <T> List<Key<T>> insertMany(final String collection, final List<T> entities) {
        if (entities.isEmpty()) {
            return emptyList();
        }
        return insertMany(collection, entities, new InsertManyOptions(), enforceWriteConcern(entities.get(0).getClass()));
    }

    @Override
    public <T> List<Key<T>> insertMany(final String collection, final List<T> entities, final InsertManyOptions options,
                                       final WriteConcern writeConcern) {
        if (entities.isEmpty()) {
            return emptyList();
        }
        final Class<?> entityClass = entities.get(0).getClass();
        return insertMany(getDatabase().getCollection(collection, (Class<T>) entityClass), entities, options,
            enforceWriteConcern(entityClass));
    }

    private <T> List<Key<T>> insertMany(final MongoCollection<T> collection, final List<T> entities, final InsertManyOptions options,
                                        final WriteConcern wc) {

        final Map<Boolean, List<T>> grouped = entities.stream()
                                                      .collect(groupingBy(
                                                          entity -> mapper.getMappedClass(entity)
                                                                          .getMappedVersionField() != null));
        if (grouped.get(TRUE) != null) {
            final InsertOneOptions insertOneOptions = new InsertOneOptions()
                                                          .bypassDocumentValidation(options.getBypassDocumentValidation());
            grouped.get(TRUE).forEach(e -> save(e, insertOneOptions, wc));
        }
        if (grouped.get(FALSE) != null) {
            collection
                .withWriteConcern(wc)
                .insertMany(grouped.get(FALSE), options);
        }

        return postSaveOperations(entities);
    }

    private <T> Key<T> insertOne(final MongoCollection<T> collection, final T entity, final InsertOneOptions options,
                                 final WriteConcern wc) {
        collection
            .withWriteConcern(wc)
            .insertOne(entity, options);

        return postSaveOperations(singletonList(entity)).get(0);
    }

    @Override
    public <T> Query<T> queryByExample(final String collection, final T ex) {
        return queryByExample(getDatabase().getCollection(collection, (Class<T>) ex.getClass()), ex);
    }

    @Override
    public <T> Key<T> save(final String collection, final T entity) {
        return save(collection, entity, new InsertOneOptions(), enforceWriteConcern(entity.getClass()));
    }

    @Override
    public <T> Key<T> save(final String collection, final T entity, final InsertOneOptions options, final WriteConcern writeConcern) {
        return save(getCollection(collection, (Class<T>) entity.getClass()), entity, options, writeConcern);
    }

    @Override
    public <T> List<Key<T>> saveMany(final List<T> entities) {
        if (entities.isEmpty()) {
            return emptyList();
        }
        return saveMany(entities, new InsertManyOptions(), enforceWriteConcern(entities.get(0).getClass()));
    }

    @Override
    public <T> List<Key<T>> saveMany(final List<T> entities, final InsertManyOptions options, final WriteConcern wc) {
        if (entities.isEmpty()) {
            return emptyList();
        }

        final Map<String, List<T>> map = entities.stream()
                                                             .collect(groupingBy(entity -> {
                                                                 final Class<T> aClass = (Class<T>) entity.getClass();
                                                                 return getCollection(aClass).getNamespace().getCollectionName();
                                                             }));

        map.forEach((key, value) -> {
            ((MongoCollection) getCollection(key, value.get(0).getClass()))
                .withWriteConcern(wc)
                .insertMany(value, options);
        });

        return entities.stream().map(mapper::getKey)
                       .collect(Collectors.toList());
    }

    @Override
    public <T> Key<T> save(final T entity) {
        validateEntityOnSave(entity);
        return save(entity, new InsertOneOptions(), enforceWriteConcern(entity.getClass()));
    }

    @Override
    public <T> Key<T> save(final T entity, final InsertOneOptions options, final WriteConcern writeConcern) {
        validateEntityOnSave(entity);

        final MongoCollection<T> collection = (MongoCollection<T>) getCollection(entity.getClass());
        return save(collection, entity, options, writeConcern);
    }

    private <T> void validateEntityOnSave(final T entity) {
        if (entity == null) {
            throw new UpdateException("Can not persist a null entity");
        }
    }

    private <T> Key<T> save(final MongoCollection<T> collection, final T entity, final InsertOneOptions options,
                            final WriteConcern writeConcern) {
        validateEntityOnSave(entity);

        if (tryVersionedUpdate(collection, entity, writeConcern) == null) {
            final MongoCollection<T> mongoCollection = collection
                                                           .withWriteConcern(writeConcern);
            final Object id = mapper.getMappedClass(entity).getMappedIdField()
                                    .getFieldValue(entity);
            if (id == null
                || updateEntity(mongoCollection, entity, new Document("_id", id), new UpdateOptions().upsert(true)) == null) {
                mongoCollection
                    .insertOne(entity, options);
            }
        }

        return postSaveOperations(singletonList(entity)).get(0);
    }

    private <T> UpdateResult updateEntity(final MongoCollection<T> mongoCollection, final T entity,
                                          final Document query, final UpdateOptions options) {

        final BsonDocument document = new BsonDocument();
        getCodecRegistry().get((Class<T>) entity.getClass())
                          .encode(new BsonDocumentWriter(document), entity, EncoderContext.builder().build());
        document.remove(Mapper.ID_KEY);
        if (document.isEmpty()) {
            return null;
        } else {
            return mongoCollection.updateOne(query, new Document("$set", document), options);
        }
    }

    @Override
    public <T> MongoCollection<T> getCollection(final Class<T> clazz) {
        return getDatabase().getCollection(mapper.getCollectionName(clazz), clazz);
    }

    private <T> MongoCollection<T> getCollection(final String collectionName, final Class<T> aClass) {
        return collectionName == null ? null : getDatabase().getCollection(collectionName, aClass);
    }

    private <T> UpdateResult update(final Query<T> query, final UpdateOperations<T> update) {
        return updateOne(query, update, new UpdateOptions()
                                         .upsert(false), enforceWriteConcern(query.getEntityClass()));
    }

    @Override
    public <T> UpdateResult update(final T entity, final UpdateOperations<T> operations) {
        if (entity instanceof Query) {
            return update((Query<T>) entity, operations);
        }

        final MappedClass mc = mapper.getMappedClass(entity);
        final Class<T> clazz = (Class<T>) mapper.getMappedClass(entity).getClazz();
        Query<T> query = createQuery(clazz)
                             .disableValidation()
                             .filter(Mapper.ID_KEY, mapper.getId(entity));
        if (!mc.getFieldsAnnotatedWith(Version.class).isEmpty()) {
            final MappedField field = mc.getFieldsAnnotatedWith(Version.class).get(0);
            query.field(field.getNameToStore()).equal(field.getFieldValue(entity));
        }

        return update(query, operations);
    }

    @Override
    public <T> UpdateResult update(final Key<T> key, final UpdateOperations<T> operations, final UpdateOptions options,
                                   final WriteConcern writeConcern) {
        Class<T> clazz = (Class<T>) key.getType();
        if (clazz == null) {
            clazz = mapper.getClassFromCollection(key.getCollection());
        }
        return updateOne(createQuery(clazz).disableValidation().filter(Mapper.ID_KEY, key.getId()), operations, options, writeConcern);
    }

    @Override
    public <T> UpdateResult updateOne(final Query<T> query, final UpdateOperations<T> operations) {
        return updateOne(query, operations, new UpdateOptions().upsert(false), enforceWriteConcern(query.getEntityClass()));
    }

    @Override
    public <T> UpdateResult updateOne(final Query<T> query, final UpdateOperations<T> operations, final UpdateOptions options,
                                      final WriteConcern writeConcern) {
        return updateOne(query, operations.getOperations(), options, writeConcern);
    }

    private <T> UpdateResult updateOne(final Query<T> query, final Document update, final UpdateOptions options,
                                       final WriteConcern writeConcern) {

        MongoCollection<T> collection = query.getCollection();
        // TODO remove this after testing.
        if (collection == null) {
            collection = getCollection(query.getEntityClass());
        }

        if (query.getSortDocument() != null && !query.getSortDocument().keySet().isEmpty()) {
            throw new QueryException("sorting is not allowed for updates.");
        }

        Document queryObject = query.getQueryDocument();

        final MappedClass mc = getMapper().getMappedClass(query.getEntityClass());
        final List<MappedField> fields = mc.getFieldsAnnotatedWith(Version.class);
        if (!fields.isEmpty()) {
            final MappedField versionMF = fields.get(0);
            if (update.get(versionMF.getNameToStore()) == null) {
                if (!update.containsKey("$inc")) {
                    update.put("$inc", new Document(versionMF.getNameToStore(), 1));
                } else {
                    ((Map<String, Object>) (update.get("$inc"))).put(versionMF.getNameToStore(), 1);
                }
            }
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace(format("Executing update(%s) for query: %s, ops: %s, upsert: %s",
                collection.getNamespace().getCollectionName(), queryObject, update, options.isUpsert()));
        }

        return collection.withWriteConcern(writeConcern)
                         .updateOne(queryObject, update, options);
    }

    @Override
    public <T> UpdateResult updateMany(final Query<T> query, final UpdateOperations<T> operations) {
        return updateMany(query, operations, new UpdateOptions().upsert(false), enforceWriteConcern(query.getEntityClass()));
    }

    @Override
    public <T> UpdateResult updateMany(final Query<T> query, final UpdateOperations<T> operations, final UpdateOptions options,
                                   final WriteConcern writeConcern) {
        MongoCollection<T> collection = query.getCollection();
        // TODO remove this after testing.
        if (collection == null) {
            collection = getCollection(query.getEntityClass());
        }

        final MappedClass mc = getMapper().getMappedClass(query.getEntityClass());
        final List<MappedField> fields = mc.getFieldsAnnotatedWith(Version.class);

        Document queryObject = query.getQueryDocument();

        if (!fields.isEmpty()) {
            operations.inc(fields.get(0).getNameToStore(), 1);
        }

        final Document update = operations.getOperations();
        if (LOG.isTraceEnabled()) {
            LOG.trace(format("Executing update(%s) for query: %s, ops: %s,upsert: %s",
                collection.getNamespace().getCollectionName(), queryObject, update, options.isUpsert()));
        }

        return collection.withWriteConcern(writeConcern)
                         .updateMany(queryObject, update, options);
    }
}
