package xyz.morphia;


import com.mongodb.MapReduceCommand;
import com.mongodb.WriteConcern;
import com.mongodb.client.MapReduceIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.DeleteOptions;
import com.mongodb.client.model.FindOneAndDeleteOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.InsertOneOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import xyz.morphia.aggregation.AggregationPipeline;
import xyz.morphia.annotations.Indexed;
import xyz.morphia.annotations.Indexes;
import xyz.morphia.annotations.Text;
import xyz.morphia.annotations.Validation;
import xyz.morphia.mapping.Mapper;
import xyz.morphia.query.Query;
import xyz.morphia.query.QueryFactory;
import xyz.morphia.query.UpdateOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * Datastore interface to get/delete/save objects
 */
public interface Datastore {
    /**
     * Returns a new query bound to the kind (a specific {@link MongoCollection})
     *
     * @param source The class to create aggregation against
     * @return the aggregation pipeline
     */
    AggregationPipeline createAggregation(Class source);

    /**
     * Returns a new query bound to the collection (a specific {@link MongoCollection})
     *
     * @param collection The collection to query
     * @param <T>        the type of the query
     * @return the query
     */
    <T> Query<T> createQuery(Class<T> collection);

    /**
     * The builder for all update operations
     *
     * @param clazz the type to update
     * @param <T>   the type to update
     * @return the new UpdateOperations instance
     */
    <T> UpdateOperations<T> createUpdateOperations(Class<T> clazz);

    /**
     * Deletes the given entity (by id)
     *
     * @param clazz the type to delete
     * @param id    the ID of the entity to delete
     * @param <T>   the type to delete
     * @param <V>   the type of the id
     * @return results of the delete
     */
    <T, V> DeleteResult deleteOne(Class<T> clazz, V id);

    /**
     * Deletes the given entity (by id)
     *
     * @param clazz the type to delete
     * @param id    the ID of the entity to delete
     * @param options the options to use when deleting
     * @param writeConcern the WriteConcern to apply
     * @param <T>   the type to delete
     * @param <V>   the type of the id
     * @return results of the delete
     * @since 1.3
     */
    <T, V> DeleteResult deleteOne(Class<T> clazz, V id, DeleteOptions options, WriteConcern writeConcern);

    /**
     * Deletes the given entities (by id)
     *
     * @param clazz the type to delete
     * @param ids   the IDs of the entity to delete
     * @param <T>   the type to delete
     * @param <V>   the type of the id
     * @return results of the delete
     */
    <T, V> DeleteResult deleteMany(Class<T> clazz, List<V> ids);

    /**
     * Deletes the given entities (by id)
     *
     * @param clazz the type to delete
     * @param ids   the IDs of the entity to delete
     * @param options the options to use when deleting
     * @param writeConcern the WriteConcern to apply
     * @param <T>   the type to delete
     * @param <V>   the type of the id
     * @return results of the delete
     * @since 1.3
     */
    <T, V> DeleteResult deleteMany(Class<T> clazz, List<V> ids, DeleteOptions options, WriteConcern writeConcern);

    /**
     * Deletes entities based on the query
     *
     * @param query the query to use when finding documents to delete
     * @param <T>   the type to delete
     * @return results of the delete
     */
    <T> DeleteResult deleteMany(Query<T> query);

    /**
     * Deletes entities based on the query
     *
     * @param query   the query to use when finding documents to delete
     * @param options the options to apply to the delete
     * @param writeConcern the WriteConcern to apply
     * @param <T>     the type to delete
     * @return results of the delete
     * @since 1.3
     */
    <T> DeleteResult deleteMany(Query<T> query, DeleteOptions options, WriteConcern writeConcern);

    /**
     * Deletes the given entity (by @Id)
     *
     * @param entity the entity to delete
     * @param <T>    the type to delete
     * @return results of the delete
     */
    <T> DeleteResult deleteOne(T entity);

    /**
     * Deletes the given entity (by @Id), with the WriteConcern
     *
     * @param entity  the entity to delete
     * @param options the options to use when deleting
     * @param writeConcern the WriteConcern to apply
     * @param <T>     the type to delete
     * @return results of the delete
     * @since 1.3
     */
    <T> DeleteResult deleteOne(T entity, DeleteOptions options, WriteConcern writeConcern);

    /**
     * ensure capped collections for {@code Entity}(s)
     */
    void ensureCaps();

    /**
     * Process any {@link Validation} annotations for document validation.
     *
     * @since 1.3
     * @mongodb.driver.manual core/document-validation/
     */
    void enableDocumentValidation();

    /**
     * Ensures (creating if necessary) the indexes found during class mapping
     *
     * @see Indexes
     * @see Indexed
     * @see Text
     */
    void ensureIndexes();

    /**
     * Ensures (creating if necessary) the indexes found during class mapping (using {@code @Indexed, @Indexes)} on the given collection
     * name, possibly in the background
     *
     * @param background if true, the index will be built in the background.  If false, background indexing is deferred to the annotation
     *                   definition
     *
     * @see Indexes
     * @see Indexed
     * @see Text
     */
    void ensureIndexes(boolean background);

    /**
     * Ensures (creating if necessary) the indexes found during class mapping
     *
     * @param clazz the class from which to get the index definitions
     * @param <T>   the type to index
     *
     * @see Indexes
     * @see Indexed
     * @see Text
     */
    <T> void ensureIndexes(Class<T> clazz);

    /**
     * Ensures (creating if necessary) the indexes found during class mapping
     *
     * @param clazz      the class from which to get the index definitions
     * @param background if true, the index will be built in the background.  If false, background indexing is deferred to the annotation
     *                   definition
     * @param <T>        the type to index
     *
     * @see Indexes
     * @see Indexed
     * @see Text
     */
    <T> void ensureIndexes(Class<T> clazz, boolean background);

    /**
     * Does a query to check if the keyOrEntity exists in mongodb
     *
     * @param keyOrEntity the value to check for
     * @return the key if the entity exists
     */
    Key<?> exists(Object keyOrEntity);

    /**
     * Find all instances by type
     *
     * @param clazz the class to use for mapping the results
     * @param <T>   the type to query
     * @return the query
     */
    <T> Query<T> find(Class<T> clazz);

    /**
     * Deletes the given entities based on the query (first item only).
     *
     * @param query the query to use when finding entities to delete
     * @param <T>   the type to query
     * @return the deleted Entity
     */
    <T> T findAndDelete(Query<T> query);

    /**
     * Deletes the given entities based on the query (first item only).
     *
     * @param query the query to use when finding entities to delete
     * @param options the options to apply to the delete
     * @param writeConcern the WriteConcern to apply
     * @param <T>   the type to query
     * @return the deleted Entity
     * @since 1.3
     */
    <T> T findAndDelete(Query<T> query, FindOneAndDeleteOptions options, WriteConcern writeConcern);

    /**
     * Find the first Entity from the Query, and modify it.
     *
     * @param query      the query to use when finding entities to update
     * @param operations the updates to apply to the matched documents
     * @param <T>        the type to query
     * @return The modified Entity (the result of the update)
     */
    <T> T findAndModify(Query<T> query, UpdateOperations<T> operations);

    /**
     * Find the first Entity from the Query, and modify it.
     *
     * @param query      the query to use when finding entities to update
     * @param operations the updates to apply to the matched documents
     * @param options    the options to apply to the update
     * @param writeConcern the WriteConcern to apply
     * @param <T>        the type to query
     * @return The modified Entity (the result of the update)
     * @since 1.3
     */
    <T> T findAndModify(Query<T> query, UpdateOperations<T> operations, FindOneAndUpdateOptions options, WriteConcern writeConcern);

    /**
     * Find the given entity (by collectionName/id); think of this as refresh
     *
     * @param entity The entity to search for
     * @param <T>    the type to fetch
     * @return the matched entity.  may be null.
     */
    <T> T get(T entity);

    /**
     * Find the given entity (by id); shorthand for {@code find("_id ", id)}
     *
     * @param clazz the class to use for mapping
     * @param id    the ID to query
     * @param <T>   the type to fetch
     * @param <V>   the type of the ID
     * @return the matched entity.  may be null.
     */
    <T, V> T get(Class<T> clazz, V id);

    /**
     * Find the given entities (by id); shorthand for {@code find("_id in", ids)}
     *
     * @param clazz the class to use for mapping
     * @param ids   the IDs to query
     * @param <T>   the type to fetch
     * @param <V>   the type of the ID
     * @return the query to find the entities
     */
    <T, V> Query<T> get(Class<T> clazz, List<V> ids);

    /**
     * Find the given entity (by collectionName/id);
     *
     * @param clazz the class to use for mapping
     * @param key   the key search with
     * @param <T>   the type to fetch
     * @return the matched entity.  may be null.
     */
    <T> T getByKey(Class<T> clazz, Key<T> key);

    /**
     * Find the given entities (by id), verifying they are of the correct type; shorthand for {@code find("_id in", ids)}
     *
     * @param clazz the class to use for mapping
     * @param keys  the keys to search with
     * @param <T>   the type to fetch
     * @return the matched entities.  may be null.
     */
    <T> List<T> getByKeys(Class<T> clazz, List<Key<T>> keys);

    /**
     * Find the given entities (by id); shorthand for {@code find("_id in", ids)}
     *
     * @param keys the keys to search with
     * @param <T>  the type to fetch
     * @return the matched entities.  may be null.
     */
    <T> List<T> getByKeys(List<Key<T>> keys);

    /**
     * @param clazz the class to use for mapping
     * @param <T>  the type to lookup
     * @return the mapped collection for the collection
     */
    <T> MongoCollection<T> getCollection(Class<T> clazz);

    /**
     * Gets the count this kind ({@link MongoCollection})
     *
     * @param entity The entity whose type to count
     * @param <T>    the type to count
     * @return the count
     */
    <T> long getCount(T entity);

    /**
     * Gets the count this kind ({@link MongoCollection})
     *
     * @param clazz The clazz type to count
     * @param <T>   the type to count
     * @return the count
     */
    <T> long getCount(Class<T> clazz);

    /**
     * Gets the count of items returned by this query; same as {@code query.countAll()}
     *
     * @param query the query to filter the documents to count
     * @param <T>   the type to count
     * @return the count
     */
    <T> long getCount(Query<T> query);

    /**
     * Gets the count of items returned by this query; same as {@code query.countAll()}
     *
     * @param query   the query to filter the documents to count
     * @param <T>     the type to count
     * @param options the options to apply to the count
     * @return the count
     * @since 1.3
     */
    <T> long getCount(Query<T> query, CountOptions options);

    /**
     * @return the database this Datastore uses
     */
    MongoDatabase getDatabase();

    /**
     * @return the default WriteConcern used by this Datastore
     */
    WriteConcern getDefaultWriteConcern();

    /**
     * Sets the default WriteConcern for this Datastore
     *
     * @param wc the default WriteConcern to be used by this Datastore
     */
    void setDefaultWriteConcern(WriteConcern wc);

    /**
     * Creates a (type-safe) reference to the entity.
     *
     * @param entity the entity whose key is to be returned
     * @param <T>    the type of the entity
     * @return the Key
     */
    <T> Key<T> getKey(T entity);

    /**
     * @return the current {@link QueryFactory}.
     * @see QueryFactory
     */
    QueryFactory getQueryFactory();

    /**
     * Replaces the current {@link QueryFactory} with the given value.
     *
     * @param queryFactory the QueryFactory to use
     * @see QueryFactory
     */
    void setQueryFactory(QueryFactory queryFactory);

    /**
     * Work as if you did an update with each field in the entity doing a $set; Only at the top level of the entity.
     *
     * @param <T>    the type of the entity
     * @param entity the entity to merge back in to the database
     */
    <T> void merge(T entity);

    /**
     * Work as if you did an update with each field in the entity doing a $set; Only at the top level of the entity.
     *
     * @param entity the entity to merge back in to the database
     * @param options the options to apply
     * @param wc     the WriteConcern to use
     * @param <T>    the type of the entity
     */
    <T> void merge(T entity, InsertOneOptions options, WriteConcern wc);

    /**
     * Returns a new query based on the example object
     *
     * @param example the example entity to use when creating the query
     * @param <T>     the type of the entity
     * @return the query
     */
    <T> Query<T> queryByExample(T example);

    /**
     * @return the Mapper used by this Datastore
     */
    Mapper getMapper();

    /**
     * Saves the entities (Objects) and updates the @Id field
     *
     * @param entities the entities to save
     * @param <T>      the type of the entity
     * @return the keys of the entities
     */
    <T> List<Key<T>> saveMany(List<T> entities);

    /**
     * Saves the entities (Objects) and updates the @Id field, with the WriteConcern
     *
     * @param entities the entities to save
     * @param <T>      the type of the entity
     * @param options  the options to apply to the save operation
     * @param writeConcern the WriteConcern to apply
     * @return the keys of the entities
     */
    <T> List<Key<T>> saveMany(List<T> entities, InsertManyOptions options, WriteConcern writeConcern);

    /**
     * Saves an entity (Object) and updates the @Id field
     *
     * @param entity the entity to save
     * @param <T>    the type of the entity
     * @return the keys of the entity
     */
    <T> Key<T> save(T entity);

    /**
     * Saves an entity (Object) and updates the @Id field
     *
     * @param entity       the entity to save
     * @param <T>          the type of the entity
     * @param options the options to apply to the save operation
     * @param writeConcern the WriteConcern to use for this operation
     * @return the keys of the entity
     */
    <T> Key<T> save(T entity, InsertOneOptions options, WriteConcern writeConcern);

    /**
     * Updates an entity with the operations; this is an atomic operation
     *
     * @param entity     the entity to update
     * @param operations the update operations to perform
     * @param <T>        the type of the entity
     * @return the update results
     * @see UpdateResult
     */
    <T> UpdateResult update(T entity, UpdateOperations<T> operations);

    /**
     * Updates an entity with the operations; this is an atomic operation
     *
     * @param key        the key of entity to update
     * @param operations the update operations to perform
     * @param options the options to apply
     * @param writeConcern the WriteConcern to apply
     * @param <T>        the type of the entity
     * @return the update results
     * @see UpdateResult
     */
    <T> UpdateResult update(Key<T> key, UpdateOperations<T> operations, UpdateOptions options, WriteConcern writeConcern);

    /**
     * Updates the first entity found with the operations; this is an atomic operation per entity
     *
     * @param query      the query used to match the documents to update
     * @param operations the update operations to perform
     * @param <T>        the type of the entity
     * @return the results of the updates
     */
    <T> UpdateResult updateOne(Query<T> query, UpdateOperations<T> operations);

    /**
     * Updates the first entity found with the operations; this is an atomic operation per entity
     *
     * @param query        the query used to match the documents to update
     * @param operations   the update operations to perform
     * @param options      the options to apply to the update
     * @param writeConcern the WriteConcern to use
     * @param <T>          the type of the entity
     * @return the results of the updates
     * @since 1.3
     */
    <T> UpdateResult updateOne(Query<T> query, UpdateOperations<T> operations, UpdateOptions options, WriteConcern writeConcern);

    /**
     * Updates all entities found with the operations; this is an atomic operation per entity
     *
     * @param query      the query used to match the documents to update
     * @param operations the update operations to perform
     * @param <T>        the type of the entity
     * @return the results of the updates
     * @since 1.3
     */
    <T> UpdateResult updateMany(Query<T> query, UpdateOperations<T> operations);
    /**
     * Updates all entities found with the operations; this is an atomic operation per entity
     *
     * @param query      the query used to match the documents to update
     * @param operations the update operations to perform
     * @param options    the options to apply to the update
     * @param writeConcern the WriteConcern to apply
     * @param <T>        the type of the entity
     * @return the results of the updates
     * @since 1.3
     */
    <T> UpdateResult updateMany(Query<T> query, UpdateOperations<T> operations, UpdateOptions options, WriteConcern writeConcern);
}
