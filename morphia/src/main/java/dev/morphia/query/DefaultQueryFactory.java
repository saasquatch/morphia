package dev.morphia.query;

import com.mongodb.DBObject;

import dev.morphia.Datastore;

/**
 * A default implementation of {@link QueryFactory}.
 */
public class DefaultQueryFactory extends AbstractQueryFactory {

    @Override
    public <T> Query<T> createQuery(final Datastore datastore, final Class<T> type, final DBObject query) {

        final QueryImpl<T> item = new QueryImpl<T>(type, datastore);

        if (query != null) {
            item.setQueryObject(query);
        }

        return item;
    }

}
