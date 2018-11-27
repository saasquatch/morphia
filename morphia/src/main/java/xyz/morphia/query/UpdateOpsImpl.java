package xyz.morphia.query;


import com.mongodb.client.model.PushOptions;
import org.bson.Document;
import xyz.morphia.internal.PathTarget;
import xyz.morphia.mapping.MappedField;
import xyz.morphia.mapping.Mapper;
import xyz.morphia.mapping.experimental.PropertyHandler;

import java.util.List;

import static java.util.Collections.singletonList;


/**
 * @param <T> the type to update
 */
public class UpdateOpsImpl<T> implements UpdateOperations<T> {
    private final Mapper mapper;
    private final Class<T> clazz;
    private Document operations = new Document();
    private boolean validateNames = true;

    /**
     * Creates an UpdateOpsImpl for the type given.
     *
     * @param type   the type to update
     * @param mapper the Mapper to use
     */
    public UpdateOpsImpl(final Class<T> type, final Mapper mapper) {
        this.mapper = mapper;
        clazz = type;
    }

    @Override
    public UpdateOperations<T> addToSet(final String field, final Object value) {
        if (value == null) {
            throw new QueryException("Value cannot be null.");
        }

        add(UpdateOperator.ADD_TO_SET, field, value);
        return this;
    }

    @Override
    public UpdateOperations<T> addToSet(final String field, final List<?> values) {
        if (values == null || values.isEmpty()) {
            throw new QueryException("Values cannot be null or empty.");
        }

        add(UpdateOperator.ADD_TO_SET_EACH, field, values);
        return this;
    }

    @Override
    public UpdateOperations<T> dec(final String field) {
        return inc(field, -1);
    }

    @Override
    public UpdateOperations<T> dec(final String field, final Number value) {
        if ((value instanceof Long) || (value instanceof Integer)) {
            return inc(field, (value.longValue() * -1));
        }
        if ((value instanceof Double) || (value instanceof Float)) {
            return inc(field, (value.doubleValue() * -1));
        }
        throw new IllegalArgumentException(
            "Currently only the following types are allowed: integer, long, double, float.");
    }

    @Override
    public UpdateOperations<T> disableValidation() {
        validateNames = false;
        return this;
    }

    @Override
    public UpdateOperations<T> enableValidation() {
        validateNames = true;
        return this;
    }

    @Override
    public UpdateOperations<T> inc(final String field) {
        return inc(field, 1);
    }

    @Override
    public UpdateOperations<T> inc(final String field, final Number value) {
        if (value == null) {
            throw new QueryException("Value cannot be null.");
        }
        add(UpdateOperator.INC, field, value);
        return this;
    }

    /**
     * @return the operations listed
     */
    @Override
    public Document getOperations() {
        return new Document(operations);
    }

    /**
     * Sets the operations for this UpdateOpsImpl
     *
     * @param operations the operations
     */
    @SuppressWarnings("unchecked")
    public void setOperations(final Document operations) {
        this.operations = operations;
    }

    @Override
    public UpdateOperations<T> max(final String field, final Number value) {
        add(UpdateOperator.MAX, field, value);
        return this;
    }

    @Override
    public UpdateOperations<T> min(final String field, final Number value) {
        add(UpdateOperator.MIN, field, value);
        return this;
    }

    @Override
    public UpdateOperations<T> push(final String field, final Object value) {
        return push(field, value instanceof List ? (List<?>) value : singletonList(value), new PushOptions());
    }

    @Override
    public UpdateOperations<T> push(final String field, final Object value, final PushOptions options) {
        return push(field, value instanceof List ? (List<?>) value : singletonList(value), options);
    }

    @Override
    public UpdateOperations<T> push(final String field, final List<?> values) {
        return push(field, values, new PushOptions());
    }

    @Override
    public UpdateOperations<T> push(final String field, final List<?> values, final PushOptions options) {
        if (values == null || values.isEmpty()) {
            throw new QueryException("Values cannot be null or empty.");
        }

        PathTarget pathTarget = new PathTarget(mapper, mapper.getMappedClass(clazz), field);
        if (!validateNames) {
            pathTarget.disableValidation();
        }

        final Document val = new Document(UpdateOperator.EACH.val(), mapValue(pathTarget, values));
        append(val, "$position", options.getPosition());
        append(val, "$slice", options.getSlice());
        append(val, "$sort", options.getSortDocument());
        addOperation(UpdateOperator.PUSH, pathTarget.translatedPath(), val);

        return this;
    }

    @Override
    public UpdateOperations<T> removeAll(final String field, final Object value) {
        if (value == null) {
            throw new QueryException("Value cannot be null.");
        }
        add(UpdateOperator.PULL, field, value);
        return this;
    }

    @Override
    public UpdateOperations<T> removeAll(final String field, final List<?> values) {
        if (values == null || values.isEmpty()) {
            throw new QueryException("Value cannot be null or empty.");
        }

        add(UpdateOperator.PULL_ALL, field, values);
        return this;
    }

    @Override
    public UpdateOperations<T> removeFirst(final String field) {
        return remove(field, true);
    }

    @Override
    public UpdateOperations<T> removeLast(final String field) {
        return remove(field, false);
    }

    @Override
    public UpdateOperations<T> set(final String field, final Object value) {
        if (value == null) {
            throw new QueryException("Value cannot be null.");
        }

        add(UpdateOperator.SET, field, value);
        return this;
    }

    @Override
    public UpdateOperations<T> setOnInsert(final String field, final Object value) {
        if (value == null) {
            throw new QueryException("Value cannot be null.");
        }

        add(UpdateOperator.SET_ON_INSERT, field, value);
        return this;
    }

    @Override
    public UpdateOperations<T> unset(final String field) {
        add(UpdateOperator.UNSET, field, 1);
        return this;
    }

    protected UpdateOperations<T> remove(final String fieldExpr, final boolean firstNotLast) {
        add(UpdateOperator.POP, fieldExpr, (firstNotLast) ? -1 : 1);
        return this;
    }

    private void append(final Document document, final String key, final Object value) {
        if (value != null) {
            document.put(key, value);
        }
    }

    protected void add(final UpdateOperator op, final String f, final Object value) {
        if (value == null) {
            throw new QueryException("Val cannot be null");
        }

        PathTarget pathTarget = new PathTarget(mapper, mapper.getMappedClass(clazz), f);
        Object val = mapValue(pathTarget, value);

        if (!validateNames) {
            pathTarget.disableValidation();
        }

        if (UpdateOperator.ADD_TO_SET_EACH.equals(op)) {
            val = new Document(UpdateOperator.EACH.val(), val);
        }

        addOperation(op, pathTarget.translatedPath(), val);
    }

    private Object mapValue(final PathTarget pathTarget, final Object value) {
        final MappedField mappedField = pathTarget.getTarget();
        Object mappedValue = value;
        if (value != null && mapper.isMappable(value) && mappedField != null) {
            PropertyHandler handler = mappedField.getHandler();
            if (handler != null) {
                mappedValue = handler.encodeValue(value);
            }
        }
        return mappedValue;
    }

    private void addOperation(final UpdateOperator op, final String fieldName, final Object val) {
        final String opString = op.val();

        Document operation = (Document) operations.get(opString);
        if (operation == null) {
            operation = new Document();
            operations.put(opString, operation);
        }
        operation.put(fieldName, val);
    }

    @Override
    public String toString() {
        return operations.toString();
    }
}
