package xyz.morphia.aggregation;

import com.mongodb.AggregationOptions;
import com.mongodb.ReadPreference;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoIterable;
import org.bson.Document;
import xyz.morphia.Datastore;
import xyz.morphia.internal.DatastoreImpl;
import xyz.morphia.logging.Logger;
import xyz.morphia.logging.MorphiaLoggerFactory;
import xyz.morphia.query.Query;
import xyz.morphia.query.Sort;

import java.util.ArrayList;
import java.util.List;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Implementation of an AggregationPipeline.
 */
@SuppressWarnings("deprecation")
public class AggregationPipelineImpl implements AggregationPipeline {
    private static final Logger LOG = MorphiaLoggerFactory.get(AggregationPipelineImpl.class);

    private final MongoCollection<?> collection;
    private final List<Document> stages = new ArrayList<>();
    private final Datastore datastore;

    /**
     * Creates an AggregationPipeline
     *
     * @param datastore the datastore to use
     * @param collection the database collection on which to operate
     */
    public AggregationPipelineImpl(final Datastore datastore, final MongoCollection<?> collection) {
        this.datastore = datastore;
        this.collection = collection;
    }

    /**
     * Returns the internal list of stages for this pipeline.  This is an internal method intended only for testing and validation.  Use
     * at your own risk.
     *
     * @return the list of stages
     */
    List<Document> getStages() {
        return stages;
    }

    @Override
    public <U> AggregateIterable<U> aggregate(final Class<U> target) {
        return aggregate(target, collection.getReadPreference());
    }

    @Override
    public <U> AggregateIterable<U> aggregate(final Class<U> target, final ReadPreference readPreference) {
        LOG.debug("stages = " + stages);

        return collection
                   .withReadPreference(readPreference)
                  .aggregate(stages, target);
    }

    @Override
    public <U> MongoIterable<U> out(final Class<U> target) {
        return out(datastore.getCollection(target).getNamespace().getCollectionName(), target);
    }

    @Override
    public <U> MongoIterable<U> out(final String collectionName, final Class<U> target) {
        stages.add(new Document("$out", collectionName));
        return out(target, AggregationOptions.builder().build(), ReadPreference.primary());
    }

    @Override
    public <U> MongoIterable<U> out(final Class<U> target, final AggregationOptions options, final ReadPreference readPreference) {
        apply(aggregate(target, readPreference), options)
            .toCollection();

        return datastore.find(target);
    }

    @Override
    public <U> void out(final String collectionName,
                        final Class<U> target,
                        final AggregationOptions options,
                        final ReadPreference readPreference) {
        stages.add(new Document("$out", collectionName));
        out(target, options, ReadPreference.primary());
    }

    static <U> AggregateIterable<U> apply(final AggregateIterable<U> aggregate, final AggregationOptions options) {
        aggregate.bypassDocumentValidation(options.getBypassDocumentValidation());
        aggregate.allowDiskUse(options.getAllowDiskUse());
        aggregate.maxTime(options.getMaxTime(MILLISECONDS), MILLISECONDS);
        aggregate.collation(options.getCollation());
        return aggregate;
    }

    @Override
    @SuppressWarnings("deprecation")
    public AggregationPipeline geoNear(final GeoNear geoNear) {
        Document geo = new Document();

        putIfNull(geo, "near", geoNear.getLocation());
        putIfNull(geo, "distanceField", geoNear.getDistanceField());
        putIfNull(geo, "limit", geoNear.getLimit());
        putIfNull(geo, "num", geoNear.getMaxDocuments());
        putIfNull(geo, "maxDistance", geoNear.getMaxDistance());
        if (geoNear.getQuery() != null) {
            geo.put("query", geoNear.getQuery().getQueryDocument());
        }
        putIfNull(geo, "spherical", geoNear.getSpherical());
        putIfNull(geo, "distanceMultiplier", geoNear.getDistanceMultiplier());
        putIfNull(geo, "includeLocs", geoNear.getIncludeLocations());
        stages.add(new Document("$geoNear", geo));

        return this;
    }

    @Override
    public AggregationPipeline group(final Group... groupings) {
        return group((String) null, groupings);
    }

    @Override
    public AggregationPipeline group(final String id, final Group... groupings) {
        Document group = new Document();
        group.put("_id", id != null ? "$" + id : null);
        for (Group grouping : groupings) {
            group.putAll(toDocument(grouping));
        }

        stages.add(new Document("$group", group));
        return this;
    }

    @Override
    public AggregationPipeline group(final List<Group> id, final Group... groupings) {
        Document idGroup = null;
        if (id != null) {
            idGroup = new Document();
            for (Group group : id) {
                idGroup.putAll(toDocument(group));
            }
        }
        Document group = new Document("_id", idGroup);
        for (Group grouping : groupings) {
            group.putAll(toDocument(grouping));
        }

        stages.add(new Document("$group", group));
        return this;
    }

    @Override
    public AggregationPipeline limit(final int count) {
        stages.add(new Document("$limit", count));
        return this;
    }

    @Override
    public AggregationPipeline lookup(final String from, final String localField, final String foreignField, final String as) {
        stages.add(new Document("$lookup", new Document("from", from)
            .append("localField", localField)
            .append("foreignField", foreignField)
            .append("as", as)));
        return this;
    }

    @Override
    public AggregationPipeline match(final Query query) {
        stages.add(new Document("$match", query.getQueryDocument()));
        return this;
    }

    @Override
    public AggregationPipeline project(final Projection... projections) {
        Document document = new Document();
        for (Projection projection : projections) {
            document.putAll(toDocument(projection));
        }
        stages.add(new Document("$project", document));
        return this;
    }

    @Override
    public AggregationPipeline skip(final int count) {
        stages.add(new Document("$skip", count));
        return this;
    }

    @Override
    public AggregationPipeline sort(final Sort... sorts) {
        Document sortList = new Document();
        for (Sort sort : sorts) {
            sortList.put(sort.getField(), sort.getOrder());
        }

        stages.add(new Document("$sort", sortList));
        return this;
    }

    @Override
    public AggregationPipeline unwind(final String field) {
        stages.add(new Document("$unwind", "$" + field));
        return this;
    }

    /**
     * Converts a Projection to a Document for use by the Java driver.
     *
     * @param projection the project to apply
     * @return the Document
     */
    @SuppressWarnings("unchecked")
    private Document toDocument(final Projection projection) {
        String target = projection.getTarget();
        if (projection.getProjections() != null) {
            List<Projection> list = projection.getProjections();
            Document projections = new Document();
            for (Projection subProjection : list) {
                projections.putAll(toDocument(subProjection));
            }
            return new Document(target, projections);
        } else if (projection.getSource() != null) {
            return new Document(target, projection.getSource());
        } else if (projection.getArguments() != null) {
            if (target == null) {
                throw new UnsupportedOperationException("This case is not yet supported.");
//                return toExpressionArgs(projection.getArguments());
            } else {
                return new Document(target, toExpressionArgs(projection.getArguments()));
            }
        } else {
            return new Document(target, projection.isSuppressed() ? 0 : 1);
        }
    }

    private Document toDocument(final Group group) {
        Document document = new Document();

        if (group.getAccumulator() != null) {
            document.put(group.getName(), group.getAccumulator().toDocument());
        } else if (group.getProjections() != null) {
            final Document projection = new Document();
            for (Projection p : group.getProjections()) {
                projection.putAll(toDocument(p));
            }
            document.put(group.getName(), projection);
        } else if (group.getNested() != null) {
            document.put(group.getName(), toDocument(group.getNested()));
        } else {
            document.put(group.getName(), group.getSourceField());
        }

        return document;
    }

    private void putIfNull(final Document document, final String name, final Object value) {
        if (value != null) {
            document.put(name, value);
        }
    }

    private Object toExpressionArgs(final List<Object> args) {
        List<Object> result = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof Projection) {
                Projection projection = (Projection) arg;
                if (projection.getArguments() != null || projection.getProjections() != null || projection.getSource() != null) {
                    result.add(toDocument(projection).toBsonDocument(Document.class, ((DatastoreImpl) datastore).getCodecRegistry()));
                } else {
                    result.add("$" + projection.getTarget());
                }
            } else {
                result.add(arg);
            }
        }
        return result.size() == 1 ? (Document) result.get(0) : result;
    }

    @Override
    public String toString() {
        return stages.toString();
    }
}
