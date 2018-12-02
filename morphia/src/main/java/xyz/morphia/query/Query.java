package xyz.morphia.query;


import com.mongodb.DBCollection;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.FindOptions;
import org.bson.Document;
import org.bson.types.CodeWScope;
import org.bson.types.CodeWithScope;

import java.util.Map;


/**
 * @param <T> The type to query against
 */
public interface Query<T> extends QueryResults<T>, Cloneable, MongoIterable<T> {
    /**
     * Creates a container to hold 'and' clauses
     *
     * @param criteria the clauses to 'and' together
     * @return the container
     */
    CriteriaContainer and(Criteria... criteria);

    /**
     * Set the {@code ReadConcern} to use
     * @param readConcern the ReadConcern to use
     * @return this
     */
    Query<T> setReadConcern(ReadConcern readConcern);

    /**
     * Set the {@code ReadPreference} to use
     * @param readPreference the ReadPreference to use
     * @return this
     */
    Query<T> setReadPreference(ReadPreference readPreference);

    /**
     * Creates and returns a copy of this {@link Query}.
     *
     * @return this
     */
    Query<T> cloneQuery();

    /**
     * Creates a criteria to apply against a field
     *
     * @param field the field
     * @return the FieldEnd to define the criteria
     */
    FieldEnd<? extends CriteriaContainerImpl> criteria(String field);

    /**
     * Turns off validation (for all calls made after)
     *
     * @return this
     */
    Query<T> disableValidation();

    /**
     * Turns on validation (for all calls made after); by default validation is on
     *
     * @return this
     */
    Query<T> enableValidation();

    /**
     * Provides information on the query plan. The query plan is the plan the server uses to find the matches for a query. This information
     * may be useful when optimizing a query.
     *
     * @return Map describing the process used to return the query results.
     * @mongodb.driver.manual reference/operator/meta/explain/ explain
     */
    Map<String, Object> explain();

    /**
     * Provides information on the query plan. The query plan is the plan the server uses to find the matches for a query. This information
     * may be useful when optimizing a query.
     *
     * @param options the options to apply to the explain operation
     * @return Map describing the process used to return the query results.
     * @mongodb.driver.manual reference/operator/meta/explain/ explain
     * @since 1.3
     */
    Map<String, Object> explain(FindOptions options);

    /**
     * Fluent query interface: {@code createQuery(Ent.class).field("count").greaterThan(7)...}
     *
     * @param field the field
     * @return the FieldEnd to define the criteria
     */
    FieldEnd<? extends Query<T>> field(String field);

    /**
     * Create a filter based on the specified condition and value. </p> <p><b>Note</b>: Property is in the form of "name op" ("age
     * >").
     * <p/>
     * <p>Valid operators are ["=", "==","!=", "<>", ">", "<", ">=", "<=", "in", "nin", "all", "size", "exists"] </p>
     * <p/>
     * <p>Examples:</p>
     * <p/>
     * <ul>
     * <li>{@code filter("yearsOfOperation >", 5)}</li>
     * <li>{@code filter("rooms.maxBeds >=", 2)}</li>
     * <li>{@code filter("rooms.bathrooms exists", 1)}</li>
     * <li>{@code filter("stars in", new Long[]{3, 4}) //3 and 4 stars (midrange?)}</li>
     * <li>{@code filter("quantity mod", new Long[]{4, 0}) // customers ordered in packs of 4)}</li>
     * <li>{@code filter("age >=", age)}</li>
     * <li>{@code filter("age =", age)}</li>
     * <li>{@code filter("age", age)} (if no operator, = is assumed)</li>
     * <li>{@code filter("age !=", age)}</li>
     * <li>{@code filter("age in", ageList)}</li>
     * <li>{@code filter("customers.loyaltyYears in", yearsList)}</li>
     * </ul>
     * <p/>
     * <p>You can filter on id properties <strong>if</strong> this query is restricted to a Class<T>.
     *
     * @param condition the condition to apply
     * @param value     the value to apply against
     * @return this
     */
    Query<T> filter(String condition, Object value);

    /**
     * @return the {@link DBCollection} of the {@link Query}.
     */
    MongoCollection<T> getCollection();

    /**
     * @return the entity {@link Class}.
     */
    Class<T> getEntityClass();

    /**
     * @return the Mongo fields {@link Document}.
     */
    Document getFields();

    /**
     * @return the Mongo query {@link Document}.
     */
    Document getQueryDocument();

    /**
     * @return the Mongo sort {@link Document}.
     */
    Document getSortDocument();

    /**
     * Creates a container to hold 'or' clauses
     *
     * @param criteria the clauses to 'or' together
     * @return the container
     */
    CriteriaContainer or(Criteria... criteria);

    /**
     * Sorts based on a metadata (defines return order). Example:
     * {@code order(Meta.textScore())}  ({textScore : { $meta: "textScore" }})
     * @param sort the sort order to apply
     * @return this
     */
    Query<T> order(Meta sort);

    /**
     * Sorts based on a specified sort keys (defines return order).
     *
     * @param sorts the sort order to apply
     * @return this
     */
    Query<T> order(Sort... sorts);

    /**
     * Adds a field to the projection clause.  Passing true for include will include the field in the results.  Projected fields must all
     * be inclusions or exclusions.  You can not include and exclude fields at the same time with the exception of the _id field.  The
     * _id field is always included unless explicitly suppressed.
     *
     * @param field the field to project
     * @param include true to include the field in the results
     * @return this
     * @see <a href="https://docs.mongodb.com/manual/tutorial/project-fields-from-query-results/">Project Fields to Return from Query</a>
     */
    Query<T> project(String field, boolean include);

    /**
     * Adds an sliced array field to a projection.
     *
     * @param field the field to project
     * @param slice the options for projecting an array field
     * @return this
     * @see <a href="https://docs.mongodb.com/manual/tutorial/project-fields-from-query-results/">Project Fields to Return from Query</a>
     * @mongodb.driver.manual /reference/operator/projection/slice/ $slice
     */
    Query<T> project(String field, ArraySlice slice);

    /**
     * Adds a metadata field to a projection.
     *
     * @param meta the metadata option for projecting
     * @return this
     * @see <a href="https://docs.mongodb.com/manual/tutorial/project-fields-from-query-results/">Project Fields to Return from Query</a>
     * @mongodb.driver.manual reference/operator/projection/meta/ $meta
     */
    Query<T> project(Meta meta);

    /**
     * Limits the fields retrieved to those of the query type -- dangerous with interfaces and abstract classes
     *
     * @return this
     */
    Query<T> retrieveKnownFields();

    /**
     * Perform a text search on the content of the fields indexed with a text index..
     *
     * @param text the text to search for
     * @return the Query to enable chaining of commands
     * @mongodb.driver.manual reference/operator/query/text/ $text
     */
    Query<T> search(String text);

    /**
     * Perform a text search on the content of the fields indexed with a text index..
     *
     * @param text     the text to search for
     * @param language the language to use during the search
     * @return the Query to enable chaining of commands
     * @mongodb.driver.manual reference/operator/query/text/ $text
     */
    Query<T> search(String text, String language);

    /**
     * Limit the query using this javascript block; only one per query
     *
     * @param js the javascript block to apply
     * @return this
     */
    Query<T> where(String js);

    /**
     * Limit the query using this javascript block; only one per query
     *
     * @param js the javascript block to apply
     * @return this
     */
    Query<T> where(CodeWithScope js);

    /**
     * @param js the CodeWScope
     * @return this
     * @see #where(CodeWithScope)
     * @deprecated use #where(CodeWithScope) instead
     */
    @SuppressWarnings("unchecked")
    default Query<T> where(final CodeWScope js) {
        return where(new CodeWithScope(js.getCode(), new Document(js.getScope().toMap())));
    }
}
