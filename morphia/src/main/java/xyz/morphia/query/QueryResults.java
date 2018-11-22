package xyz.morphia.query;


import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.FindOptions;
import xyz.morphia.Key;

import java.util.List;

/**
 * The results of a query.  These results aren't materialized until a method on this interface is called.
 *
 * @param <T>
 */
public interface QueryResults<T> extends Iterable<T> {
    /**
     * Execute the query and get the results (as a {@code List<Key<T>>})  This method is provided as a convenience;
     *
     * @return returns a List of the keys of the documents returned by a query
     */
    List<Key<T>> asKeyList();

    /**
     * Execute the query and get the results.
     *
     * @return returns a List of the documents returned by a query
     */
    List<T> asList();

    /**
     * Execute the query and get the results (as a {@code List<Key<T>>})  This method is provided as a convenience;
     *
     * @param options the options to apply to the find operation
     * @return returns a List of the keys of the documents returned by a query
     * @since 1.3
     */
    List<Key<T>> asKeyList(FindOptions options);

    /**
     * Execute the query and get the results.
     *
     * @param options the options to apply to the find operation
     * @return returns a List of the documents returned by a query
     * @since 1.3
     */
    List<T> asList(FindOptions options);

    /**
     * Count the total number of values in the result, ignoring limit and offset
     *
     * @return the count
     * @since 1.3
     */
    long count();

    /**
     * Count the total number of values in the result, ignoring limit and offset
     *
     * @param options the options to apply to the count operation
     * @return the count
     * @since 1.3
     */
    long count(CountOptions options);

    /**
     * Execute the query and get the results.  This method is provided for orthogonality; Query.fetch().iterator() is identical to
     * Query.iterator().
     *
     * @return an Iterator of the results
     */
    MongoCursor<T> fetch();

    /**
     * Execute the query and get the results.  This method is provided for orthogonality; Query.fetch().iterator() is identical to
     * Query.iterator().
     *
     * @param options the options to apply to the find operation
     * @return an Iterator of the results
     * @since 1.3
     */
    MongoCursor<T> fetch(FindOptions options);

    /**
     * Execute the query and get only the ids of the results.  This is more efficient than fetching the actual results (transfers less
     * data).
     *
     * @return an Iterator of the empty entities
     */
    MongoCursor<T> fetchEmptyEntities();

    /**
     * Execute the query and get only the ids of the results.  This is more efficient than fetching the actual results (transfers less
     * data).
     *
     * @param options the options to apply to the find operation
     * @return an Iterator of the empty entities
     * @since 1.3
     */
    MongoCursor<T> fetchEmptyEntities(FindOptions options);

    /**
     * Execute the query and get the keys for the objects.
     *
     * @return the Key Iterator
     * @see #fetchEmptyEntities
     */
    MorphiaKeyIterator<T> fetchKeys();
    /**
     * Execute the query and get the keys for the objects.
     *
     * @param options the options to apply to the find operation
     * @return the Key Iterator
     * @see #fetchEmptyEntities
     * @since 1.3
     */
    MorphiaKeyIterator<T> fetchKeys(FindOptions options);

    /**
     * Gets the first entity in the result set.  Obeys the {@link Query} offset value.
     *
     * @return the only instance in the result, or null if the result set is empty.
     */
    T get();

    /**
     * Gets the first entity in the result set.  Obeys the {@link Query} offset value.
     *
     * @param options the options to apply to the find operation
     * @return the only instance in the result, or null if the result set is empty.
     * @since 1.3
     */
    T get(FindOptions options);

    /**
     * Get the key of the first entity in the result set.  Obeys the {@link Query} offset value.
     *
     * @return the key of the first instance in the result, or null if the result set is empty.
     */
    Key<T> getKey();

    /**
     * Get the key of the first entity in the result set.  Obeys the {@link Query} offset value.
     *
     * @param options the options to apply to the find operation
     * @return the key of the first instance in the result, or null if the result set is empty.
     * @since 1.3
     */
    Key<T> getKey(FindOptions options);
}
