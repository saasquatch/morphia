package xyz.morphia;

import com.mongodb.WriteConcern;
import com.mongodb.client.model.DeleteOptions;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.InsertOneOptions;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import xyz.morphia.aggregation.AggregationPipeline;
import xyz.morphia.query.Query;
import xyz.morphia.query.UpdateOperations;

import java.util.List;

/**
 * This interface exposes advanced {@link Datastore} features, like interacting with Document and low-level options. It implements matching
 * methods from the {@code Datastore} interface but with a specified collection name, or raw types (Document).
 */
public interface AdvancedDatastore extends Datastore {

    /**
     * Returns an {@link AggregationPipeline} bound to the given collection and class.
     *
     * @param collection the collection to query
     * @param clazz      The class to create aggregation against
     * @return the aggregation pipeline
     */
    AggregationPipeline createAggregation(String collection, Class<?> clazz);

    /**
     * @param <T>        The type of the entity
     * @param collection the collection to query
     * @param clazz      the class of objects to be returned
     * @return Query for the specified class clazz
     */
    <T> Query<T> createQuery(String collection, Class<T> clazz);

    /**
     * @param <T>   The type of the entity
     * @param clazz the class of objects to be returned
     * @param q     the query which will be passed to a {@link xyz.morphia.query.QueryFactory}
     * @return Query for the specified class clazz
     */
    <T> Query<T> createQuery(Class<T> clazz, Document q);

    /**
     * @param <T>        The type of the entity
     * @param collection the collection to query
     * @param clazz      the class of objects to be returned
     * @param q          the query which will be passed to a {@link xyz.morphia.query.QueryFactory}
     * @return Query for the specified class clazz
     */
    <T> Query<T> createQuery(String collection, Class<T> clazz, Document q);

    /**
     * Creates an UpdateOperations instance for the given type.
     *
     * @param <T>        The type of the entity
     * @param type       The type of the entity
     * @param operations The operations to perform
     * @return the UpdateOperations instance
     */
    <T> UpdateOperations<T> createUpdateOperations(Class<T> type, Document operations);

    /**
     * Delete one document
     *
     * @param collectionName the document's collection
     * @param clazz          the document type
     * @param id             the ID of the document to delete
     * @param <T>            the document type
     * @param <V>            the ID type
     * @return the results of the delete operation
     */
    <T, V> DeleteResult delete(String collectionName, Class<T> clazz, V id);

    /**
     * Delete one document
     *
     * @param collectionName the document's collection
     * @param clazz          the document type
     * @param id             the ID of the document to delete
     * @param options        the options to apply
     * @param writeConcern   the WriteConcern to apply
     * @param <T>            the document type
     * @param <V>            the ID type
     * @return the results of the delete operation
     */
    <T, V> DeleteResult delete(String collectionName, Class<T> clazz, V id, DeleteOptions options, WriteConcern writeConcern);

    /**
     * Delete multiple documents
     *
     * @param collectionName the document's collection
     * @param clazz          the document type
     * @param ids            the IDs of the documents to delete
     * @param <T>            the document type
     * @param <V>            the ID type
     * @return the results of the delete operation
     */
    <T, V> DeleteResult delete(String collectionName, Class<T> clazz, List<V> ids);

    /**
     * Delete many documents
     *
     * @param collectionName the document's collection
     * @param clazz          the document type
     * @param ids            the IDs of the documents to delete
     * @param options        the options to apply
     * @param writeConcern   the WriteConcern to apply
     * @param <T>            the document type
     * @param <V>            the ID type
     * @return the results of the delete operation
     */
    <T, V> DeleteResult delete(String collectionName, Class<T> clazz, List<V> ids, DeleteOptions options, WriteConcern writeConcern);

    /**
     * Ensures (creating if necessary) the indexes found during class mapping (using {@code @Indexed, @Indexes)} on the given collection
     * name, possibly in the background
     *
     * @param collection the collection to update
     * @param clazz      the class from which to get the index definitions
     * @param background if true, the index will be built in the background.  If false, the method will block until the index is created.
     * @param <T>        the type to index
     */
    <T> void ensureIndexes(String collection, Class<T> clazz, boolean background);

    /**
     * Inserts an entity in to the mapped collection.
     *
     * @param entity the entity to insert
     * @param <T>    the type of the entity
     * @return the new key of the inserted entity
     */
    <T> Key<T> insert(T entity);

    /**
     * Inserts an entity in to the mapped collection.
     *
     * @param entity       the entity to insert
     * @param options      the options to apply to the insert operation
     * @param writeConcern the WriteConcern to apply
     * @param <T>          the type of the entity
     * @return the new key of the inserted entity
     * @since 1.3
     */
    <T> Key<T> insert(T entity, InsertOneOptions options, WriteConcern writeConcern);

    /**
     * Inserts an entity in to the named collection.
     *
     * @param collection the collection to update
     * @param entity     the entity to insert
     * @param <T>        the type of the entity
     * @return the new key of the inserted entity
     */
    <T> Key<T> insert(String collection, T entity);

    /**
     * Inserts an entity in to the named collection.
     *
     * @param collection   the collection to update
     * @param entity       the entity to insert
     * @param options      the options to apply to the insert operation
     * @param writeConcern the WriteConcern to apply
     * @param <T>          the type of the entity
     * @return the new key of the inserted entity
     * @since 1.3
     */
    <T> Key<T> insert(String collection, T entity, InsertOneOptions options, WriteConcern writeConcern);

    /**
     * Inserts entities in to the mapped collection.
     *
     * @param entities the entities to insert
     * @param <T>      the type of the entities
     * @return the new keys of the inserted entities
     */
    <T> List<Key<T>> insert(List<T> entities);

    /**
     * Inserts entities in to the mapped collection.
     *
     * @param entities     the entities to insert
     * @param options      the options to apply to the insert operation
     * @param writeConcern the WriteConcern to apply
     * @param <T>          the type of the entity
     * @return the new keys of the inserted entities
     * @since 1.3
     */
    <T> List<Key<T>> insert(List<T> entities, InsertManyOptions options, WriteConcern writeConcern);

    /**
     * Inserts an entity in to the named collection.
     *
     * @param collection the collection to update
     * @param entities   the entities to insert
     * @param <T>        the type of the entity
     * @return the new keys of the inserted entities
     * @see WriteConcern
     */
    <T> List<Key<T>> insert(String collection, List<T> entities);

    /**
     * Inserts entities in to the named collection.
     *
     * @param collection   the collection to update
     * @param entities     the entities to insert
     * @param options      the options to apply to the insert operation
     * @param writeConcern the WriteConcern to apply
     * @param <T>          the type of the entity
     * @return the new keys of the inserted entities
     * @since 1.3
     */
    <T> List<Key<T>> insert(String collection, List<T> entities, InsertManyOptions options, WriteConcern writeConcern);

    /**
     * Returns a new query based on the example object
     *
     * @param collection the collection to query
     * @param example    the example entity to use when building the query
     * @param <T>        the type of the entity
     * @return the query
     */
    <T> Query<T> queryByExample(String collection, T example);

    /**
     * Saves an entity in to the named collection.
     *
     * @param collection the collection to update
     * @param entity     the entity to save
     * @param <T>        the type of the entity
     * @return the new key of the inserted entity
     */
    <T> Key<T> save(String collection, T entity);

    /**
     * Saves an entity in to the named collection.
     *
     * @param collection   the collection to update
     * @param entity       the entity to save
     * @param options      the options to apply to the save operation
     * @param writeConcern the WriteConcern to apply
     * @param <T>          the type of the entity
     * @return the new key of the inserted entity
     */
    <T> Key<T> save(String collection, T entity, InsertOneOptions options, WriteConcern writeConcern);

    /**
     * Gets the count this collection
     *
     * @param collection the collection to count
     * @return the collection size
     * @deprecated Inline this method to update to the new usage
     */
    @Deprecated
    default long getCount(String collection) {
        return find(collection, Object.class).count();
    }

    /**
     * Find all instances by type in a different collection than what is mapped on the class given.
     *
     * @param collection the collection to query against
     * @param clazz      the class to use for mapping the results
     * @param <T>        the type to query
     * @return the query
     */
    <T> Query<T> find(String collection, Class<T> clazz);
}
