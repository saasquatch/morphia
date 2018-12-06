package xyz.morphia.query;

import com.mongodb.Block;
import com.mongodb.Function;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.FindOptions;
import org.bson.Document;
import org.bson.types.CodeWithScope;
import xyz.morphia.Datastore;
import xyz.morphia.annotations.Entity;
import xyz.morphia.internal.DatastoreImpl;
import xyz.morphia.logging.Logger;
import xyz.morphia.logging.MorphiaLoggerFactory;
import xyz.morphia.mapping.MappedClass;
import xyz.morphia.mapping.MappedField;
import xyz.morphia.mapping.Mapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static xyz.morphia.query.QueryValidator.validateQuery;


/**
 * Implementation of Query
 *
 * @param <T> The type we will be querying for, and returning.
 */
@SuppressWarnings("deprecation")
public class QueryImpl<T> extends CriteriaContainerImpl implements Query<T> {
    private static final Logger LOG = MorphiaLoggerFactory.get(QueryImpl.class);
    private final DatastoreImpl ds;
    private final MongoCollection<T> collection;
    private final Class<T> clazz;
    private boolean validateName = true;
    private boolean validateType = true;
    private Boolean includeFields;
    private Document baseQuery;
    private Document projection;
    private Document sort;
    private ReadConcern readConcern;
    private ReadPreference readPreference;

    /**
     * Creates a Query for the given type and collection
     *
     * @param clazz      the type to return
     * @param collection the collection to query
     * @param ds         the Datastore to use
     */
    public QueryImpl(final Class<T> clazz, final MongoCollection<T> collection, final Datastore ds) {
        super(CriteriaJoin.AND);

        setQuery(this);
        this.clazz = clazz;
        this.ds = (DatastoreImpl) ds;
        this.collection = collection;
        readPreference = getDatastore().getDatabase().getReadPreference();
        readConcern = getDatastore().getDatabase().getReadConcern();
    }

    /**
     * @return the Datastore
     */
    public Datastore getDatastore() {
        return ds;
    }

    @Override
    public Query<T> setReadConcern(final ReadConcern readConcern) {
        this.readConcern = readConcern;
        return this;
    }

    @Override
    public Query<T> setReadPreference(final ReadPreference readPreference) {
        this.readPreference = readPreference;
        return this;
    }

    @Override
    public QueryImpl<T> cloneQuery() {
        final QueryImpl<T> n = new QueryImpl<>(clazz, collection, ds);
        n.includeFields = includeFields;
        n.setQuery(n); // feels weird, correct?
        n.validateName = validateName;
        n.validateType = validateType;
        n.baseQuery = copy(baseQuery);
        n.sort = copy(sort);

        // fields from superclass
        n.setAttachedTo(getAttachedTo());
        n.setChildren(getChildren() == null ? null : new ArrayList<>(getChildren()));
        return n;
    }

    @Override
    public Query<T> disableValidation() {
        validateName = false;
        validateType = false;
        return this;
    }

    @Override
    public Query<T> enableValidation() {
        validateName = true;
        validateType = true;
        return this;
    }

    @Override
    public Map<String, Object> explain() {
        return explain(new FindOptions());
    }

    @Override
    public Map<String, Object> explain(final FindOptions options) {
        return new LinkedHashMap<>(getDatastore().getDatabase()
                                                 .runCommand(new Document("explain",
                                                     new Document("find", collection.getNamespace().getCollectionName())
                                                         .append("filter", getQueryDocument()))));
    }

    @Override
    public FieldEnd<? extends Query<T>> field(final String name) {
        return new FieldEndImpl<>(this, name, this);
    }

    @Override
    public Query<T> filter(final String condition, final Object value) {
        final String[] parts = condition.trim().split(" ");
        if (parts.length < 1 || parts.length > 6) {
            throw new IllegalArgumentException("'" + condition + "' is not a legal filter condition");
        }

        final String prop = parts[0].trim();
        final FilterOperator op = (parts.length == 2) ? translate(parts[1]) : FilterOperator.EQUAL;

        add(new FieldCriteria(this, prop, op, value));

        return this;
    }

    @Override
    public MongoCollection<T> getCollection() {
        return collection;
    }

    @Override
    public Class<T> getEntityClass() {
        return clazz;
    }

    @Override
    public Document getFields() {
        if (projection == null || projection.keySet().isEmpty()) {
            return null;
        }

        final MappedClass mc = ds.getMapper().getMappedClass(clazz);

        Entity entityAnnotation = mc.getEntityAnnotation();
        final Document fieldsFilter = new Document(projection);

        if (includeFields && entityAnnotation != null && entityAnnotation.useDiscriminator()) {
            fieldsFilter.put(Mapper.CLASS_NAME_FIELDNAME, 1);
        }

        return fieldsFilter;
    }

    @Override
    public Document getQueryDocument() {
        final Document obj = new Document();

        if (baseQuery != null) {
            obj.putAll(baseQuery);
        }

        addTo(obj);

        return obj;
    }

    /**
     * Sets query structure directly
     *
     * @param query the Document containing the query
     */
    void setQueryDocument(final Document query) {
        baseQuery = new Document(query);
    }

    @Override
    public Document getSortDocument() {
        return sort == null ? null : new Document(sort);
    }

    @Override
    public Query<T> order(final Meta meta) {
        validateQuery(clazz, ds.getMapper(), new StringBuilder(meta.getField()), FilterOperator.IN, "", false, false);

        if (sort == null) {
            sort = new Document();
        }
        sort.putAll(meta.toDatabase());

        return this;
    }

    @Override
    public Query<T> order(final Sort... sorts) {
        Document sortList = new Document();
        for (Sort sort : sorts) {
            String s = sort.getField();
            if (validateName) {
                final StringBuilder sb = new StringBuilder(s);
                validateQuery(clazz, ds.getMapper(), sb, FilterOperator.IN, "", true, false);
                s = sb.toString();
            }
            sortList.put(s, sort.getOrder());
        }
        if (sort == null) {
            sort = new Document();
        }
        sort.putAll(sortList);
        return this;
    }

    @Override
    public Query<T> project(final String field, final boolean include) {
        final StringBuilder sb = new StringBuilder(field);
        validateQuery(clazz, ds.getMapper(), sb, FilterOperator.EQUAL, null, validateName, false);
        String fieldName = sb.toString();
        validateProjections(fieldName, include);
        project(fieldName, include ? 1 : 0);
        return this;
    }

    @Override
    public Query<T> project(final String field, final ArraySlice slice) {
        final StringBuilder sb = new StringBuilder(field);
        validateQuery(clazz, ds.getMapper(), sb, FilterOperator.EQUAL, null, validateName, false);
        String fieldName = sb.toString();
        validateProjections(fieldName, true);
        project(fieldName, slice.toDatabase());
        return this;
    }

    @Override
    public Query<T> project(final Meta meta) {
        final StringBuilder sb = new StringBuilder(meta.getField());
        validateQuery(clazz, ds.getMapper(), sb, FilterOperator.EQUAL, null, false, false);
        String fieldName = sb.toString();
        validateProjections(fieldName, true);
        project(meta.toDatabase());
        return this;

    }

    @Override
    public Query<T> retrieveKnownFields() {
        for (final MappedField mf : ds.getMapper().getMappedClass(clazz).getPersistenceFields()) {
            project(mf.getNameToStore(), true);
        }
        return this;
    }

    @Override
    public Query<T> search(final String search) {
        final Document op = new Document("$search", search);
        this.criteria("$text").equal(op);
        return this;
    }

    @Override
    public FieldEnd<? extends CriteriaContainerImpl> criteria(final String field) {
        final CriteriaContainerImpl container = new CriteriaContainerImpl(this, CriteriaJoin.AND);
        add(container);

        return new FieldEndImpl<>(this, field, container);
    }

    @Override
    public String getFieldName() {
        return null;
    }

    @Override
    public Query<T> search(final String search, final String language) {
        final Document op = new Document("$search", search)
                                .append("$language", language);
        this.criteria("$text").equal(op);
        return this;
    }

    @Override
    public Query<T> where(final String js) {
        add(new WhereCriteria(js));
        return this;
    }

    @Override
    public Query<T> where(final CodeWithScope js) {
        add(new WhereCriteria(js));
        return this;
    }

    @Override
    public MongoCursor<T> find() {
        return find(new FindOptions());
    }

    @Override
    public MongoCursor<T> find(final FindOptions options) {
        return prepareCursor(options).iterator();
    }

    @Override
    public long count() {
        return collection.countDocuments(getQueryDocument());
    }

    @Override
    public long count(final CountOptions options) {
        return collection.countDocuments(getQueryDocument(), options);
    }

    public T get() {
        return get(new FindOptions());
    }

    public T get(final FindOptions options) {
        try (MongoCursor<T> it = find(new FindOptions(options).limit(1))) {
            return it.tryNext();
        }
    }

    private FindIterable<T> prepareCursor(final FindOptions options) {
        final Document query = getQueryDocument();

        if (options.getProjection() != null || options.getSort() != null) {
            throw new QueryException("Projections and sorting criteria must be set on the Query directly and not passed on the options "
                                     + "object.");
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace(
                String.format("Running query(%s) : %s, options: %s,", collection.getNamespace().getCollectionName(), query, options));
        }

        final MongoCollection<T> mongoCollection = collection
                                                       .withReadPreference(readPreference)
                                                       .withReadConcern(readConcern);

        final FindIterable<T> iterable = query != null && !query.isEmpty()
                                         ? mongoCollection.find(query)
                                         : mongoCollection.find();
        return apply(iterable
                         .projection(getFields())
                         .sort(sort),
            options);
    }

    private FindIterable<T> apply(final FindIterable<T> iterable, final FindOptions options) {
        iterable.batchSize(options.getBatchSize());
        iterable.collation(options.getCollation());
        iterable.limit(options.getLimit());
        iterable.maxTime(options.getMaxTime(MILLISECONDS), MILLISECONDS);
        iterable.maxAwaitTime(options.getMaxAwaitTime(MILLISECONDS), MILLISECONDS);
        iterable.skip(options.getSkip());
        iterable.cursorType(options.getCursorType());
        iterable.noCursorTimeout(options.isNoCursorTimeout());
        iterable.oplogReplay(options.isOplogReplay());
        iterable.partial(options.isPartial());
        iterable.comment(options.getComment());
        iterable.hint(options.getHint());
        iterable.max(options.getMax());
        iterable.min(options.getMin());
        iterable.maxScan(options.getMaxScan());
        iterable.returnKey(options.isReturnKey());
        iterable.showRecordId(options.isShowRecordId());
        iterable.snapshot(options.isSnapshot());

        return iterable;
    }

    private void project(final Document value) {
        if (projection == null) {
            projection = new Document();
        }
        projection.putAll(value);
    }

    private void validateProjections(final String field, final boolean include) {
        if (includeFields != null && include != includeFields) {
            if (!includeFields || !"_id".equals(field)) {
                throw new ValidationException("You cannot mix included and excluded fields together");
            }
        }
        if (includeFields == null) {
            includeFields = include;
        }
    }

    private void project(final String fieldName, final Object value) {
        if (projection == null) {
            projection = new Document();
        }
        projection.put(fieldName, value);
    }

    /**
     * Converts the textual operator (">", "<=", etc) into a FilterOperator. Forgiving about the syntax; != and <> are NOT_EQUAL, = and ==
     * are EQUAL.
     */
    private FilterOperator translate(final String operator) {
        return FilterOperator.fromString(operator);
    }

    protected Document copy(final Document document) {
        return document == null ? null : new Document(document);
    }

    boolean isValidatingNames() {
        return validateName;
    }

    boolean isValidatingTypes() {
        return validateType;
    }

    @Override
    public MongoCursor<T> iterator() {
        return find();
    }

    @Override
    public T first() {
        return first(new FindOptions());
    }

    @Override
    public T first(final FindOptions options) {
        try (MongoCursor<T> cursor = find(new FindOptions(options).limit(1))) {
            return cursor.tryNext();
        }
    }

    @Override
    public <U> MongoIterable<U> map(final Function<T, U> mapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEach(final Block<? super T> block) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <A extends Collection<? super T>> A into(final A target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MongoIterable<T> batchSize(final int batchSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
        int result = collection != null ? collection.hashCode() : 0;
        result = 31 * result + (clazz != null ? clazz.hashCode() : 0);
        result = 31 * result + (validateName ? 1 : 0);
        result = 31 * result + (validateType ? 1 : 0);
        result = 31 * result + (includeFields != null ? includeFields.hashCode() : 0);
        result = 31 * result + (baseQuery != null ? baseQuery.hashCode() : 0);
        result = 31 * result + (projection != null ? projection.hashCode() : 0);
        result = 31 * result + (sort != null ? sort.hashCode() : 0);
        result = 31 * result + (readConcern != null ? readConcern.hashCode() : 0);
        result = 31 * result + (readPreference != null ? readPreference.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final QueryImpl<?> query = (QueryImpl<?>) o;

        if (validateName != query.validateName) {
            return false;
        }
        if (validateType != query.validateType) {
            return false;
        }
        if (collection != null ? !collection.equals(query.collection) : query.collection != null) {
            return false;
        }
        if (clazz != null ? !clazz.equals(query.clazz) : query.clazz != null) {
            return false;
        }
        if (includeFields != null ? !includeFields.equals(query.includeFields) : query.includeFields != null) {
            return false;
        }
        if (baseQuery != null ? !baseQuery.equals(query.baseQuery) : query.baseQuery != null) {
            return false;
        }
        if (projection != null ? !projection.equals(query.projection) : query.projection != null) {
            return false;
        }
        if (sort != null ? !sort.equals(query.sort) : query.sort != null) {
            return false;
        }
        if (readConcern != null ? !readConcern.equals(query.readConcern) : query.readConcern != null) {
            return false;
        }
        return readPreference != null ? readPreference.equals(query.readPreference) : query.readPreference == null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        append(sb, "query", getQueryDocument());
        append(sb, "projection", getFields());
        sb.append(" }");
        return "{ " + sb + " }";
    }

    private void append(final StringBuilder values, final String key, final Object value) {
        if (value != null) {
            if (values.length() != 0) {
                values.append(", ");
            }
            values.append(key)
                  .append("=")
                  .append(value);
        }
    }
}
