package dev.morphia;


import java.util.Collections;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.DBObject;

import dev.morphia.annotations.NotSaved;
import dev.morphia.annotations.Transient;
import dev.morphia.mapping.Mapper;
import dev.morphia.mapping.cache.EntityCache;
import dev.morphia.query.Query;

/**
 * Stores the results of a map reduce operation
 *
 * @param <T> the type of the results
 * @deprecated This feature will not be supported in 2.0
 */
@NotSaved
@Deprecated
public class MapreduceResults<T> implements Iterable<T> {
    private static final Logger LOG = LoggerFactory.getLogger(MapreduceResults.class);
    private final Stats counts = new Stats();
    private String outputCollectionName;
    private Query<T> query;

    @Transient
    private Class<T> clazz;
    @Transient
    private Mapper mapper;
    @Transient
    private EntityCache cache;
    private Datastore datastore;

    /**
     * Creates a results instance for the given output
     *
     * @param output the output of the operation
     */
    public MapreduceResults() {
    }

    /**
     * @return the query to use against these results
     */
    public Query<T> createQuery() {
        return query.cloneQuery();
    }

    /**
     * @return the Stats for the operation
     */
    public Stats getCounts() {
        return counts;
    }

    /**
     * @return the duration of the operation
     */
    public long getElapsedMillis() {
    	return 0;
    }

    /**
     * @return will always return null
     */
    @Deprecated
    public String getError() {
        LOG.warn("MapreduceResults.getError() will always return null.");
        return null;
    }

    /**
     * Creates an Iterator over the results of the operation.  This method should probably not be called directly as it requires more
     * context to use properly.  Using {@link #iterator()} will return the proper Iterator regardless of the type of map reduce operation
     * performed.
     *
     * @return the Iterator
     * @see MapreduceType
     */
    public Iterator<T> getInlineResults() {
        return new dev.morphia.query.MorphiaIterator<T, T>(datastore, Collections.<DBObject>emptyIterator(), mapper, clazz, null, cache);
    }

    /**
     * @return the type of the operation
     * @deprecated use {@link #getOutputType()} instead
     */
    @Deprecated
    public MapreduceType getType() {
    	return null;
    }

    @Deprecated
    void setType(final MapreduceType type) {
    }

    /**
     * @return will always return true
     */
    @Deprecated
    public boolean isOk() {
        LOG.warn("MapreduceResults.isOk() will always return true.");
        return true;
    }

    /**
     * Creates an Iterator over the results of the operation.
     *
     * @return the Iterator
     */
    @Override
    public Iterator<T> iterator() {
    	return null;
    }

    /**
     * Sets the required options when the operation type was INLINE
     *
     * @param datastore the Datastore to use when fetching this reference
     * @param clazz     the type of the results
     * @param mapper    the mapper to use
     * @param cache     the cache of entities seen so far
     * @see OutputType
     */
    public void setInlineRequiredOptions(final Datastore datastore, final Class<T> clazz, final Mapper mapper, final EntityCache cache) {
        this.mapper = mapper;
        this.datastore = datastore;
        this.clazz = clazz;
        this.cache = cache;
    }

    /**
     * This class represents various statistics about a map reduce operation
     */
    public class Stats {
        /**
         * @return the emit count of the operation
         */
        public int getEmitCount() {
        	return 0;
        }

        /**
         * @return the input count of the operation
         */
        public int getInputCount() {
        	return 0;
        }

        /**
         * @return the output count of the operation
         */
        public int getOutputCount() {
        	return 0;
        }
    }

    String getOutputCollectionName() {
        return outputCollectionName;
    }

    void setQuery(final Query<T> query) {
        this.query = query;
    }
}
