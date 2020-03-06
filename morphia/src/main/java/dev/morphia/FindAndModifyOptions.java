/*
 * Copyright 2016 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.morphia;

import static com.mongodb.assertions.Assertions.notNull;

import java.util.concurrent.TimeUnit;

import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.Collation;

/**
 * The options for find and modify operations.
 *
 * @since 1.3
 */
public final class FindAndModifyOptions {

    /**
     * Creates a new options instance.
     */
    public FindAndModifyOptions() {
    }

    FindAndModifyOptions copy() {
        FindAndModifyOptions copy = new FindAndModifyOptions();
        copy.bypassDocumentValidation(getBypassDocumentValidation());
        copy.collation(getCollation());
        copy.maxTime(getMaxTime(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
        copy.projection(getProjection());
        copy.remove(isRemove());
        copy.returnNew(isReturnNew());
        copy.sort(getSort());
        copy.update(getUpdate());
        copy.upsert(isUpsert());
        copy.writeConcern(getWriteConcern());
        return copy;
    }

    FindAndModifyOptions getOptions() {
    	return null;
    }

    DBObject getProjection() {
    	return null;
    }

    FindAndModifyOptions projection(final DBObject projection) {
        return this;
    }

    /**
     * Returns the sort
     *
     * @return the sort
     */
    DBObject getSort() {
    	return null;
    }

    /**
     * Sets the sort
     *
     * @param sort the sort
     * @return this
     */
    FindAndModifyOptions sort(final DBObject sort) {
        return this;
    }

    /**
     * Returns the remove
     *
     * @return the remove
     */
    public boolean isRemove() {
    	return false;
    }

    /**
     * Indicates whether to remove the elements matching the query or not
     *
     * @param remove true if the matching elements should be deleted
     * @return this
     */
    public FindAndModifyOptions remove(final boolean remove) {
        return this;
    }

    /**
     * Returns the update
     *
     * @return the update
     */
    DBObject getUpdate() {
    	return null;
    }

    /**
     * Sets the update
     *
     * @param update the update
     * @return this
     */
    FindAndModifyOptions update(final DBObject update) {
        return this;
    }

    /**
     * Returns the upsert
     *
     * @return the upsert
     */
    public boolean isUpsert() {
    	return false;
    }

    /**
     * Indicates that an upsert should be performed
     *
     * @param upsert the upsert
     * @return this
     * @mongodb.driver.manual reference/method/db.collection.update/#upsert-behavior upsert
     */
    public FindAndModifyOptions upsert(final boolean upsert) {
        return this;
    }

    /**
     * Returns the returnNew
     *
     * @return the returnNew
     */
    public boolean isReturnNew() {
    	return false;
    }

    /**
     * Sets the returnNew
     *
     * @param returnNew the returnNew
     * @return this
     */
    public FindAndModifyOptions returnNew(final boolean returnNew) {
        return this;
    }

    /**
     * Returns the bypassDocumentValidation
     *
     * @return the bypassDocumentValidation
     */
    public Boolean getBypassDocumentValidation() {
    	return false;
    }

    /**
     * Sets the bypassDocumentValidation
     *
     * @param bypassDocumentValidation the bypassDocumentValidation
     * @return this
     */
    public FindAndModifyOptions bypassDocumentValidation(final Boolean bypassDocumentValidation) {
        return this;
    }

    /**
     * Gets the maximum execution time on the server for this operation.  The default is 0, which places no limit on the execution time.
     *
     * @param timeUnit the time unit to return the result in
     * @return the maximum execution time in the given time unit
     * @mongodb.driver.manual reference/method/cursor.maxTimeMS/#cursor.maxTimeMS Max Time
     */
    public long getMaxTime(final TimeUnit timeUnit) {
        notNull("timeUnit", timeUnit);
        return 0;
    }

    /**
     * Sets the maximum execution time on the server for this operation.
     *
     * @param maxTime  the max time
     * @param timeUnit the time unit, which may not be null
     * @return this
     * @mongodb.driver.manual reference/method/cursor.maxTimeMS/#cursor.maxTimeMS Max Time
     */
    public FindAndModifyOptions maxTime(final long maxTime, final TimeUnit timeUnit) {
        return this;
    }

    /**
     * Returns the writeConcern
     *
     * @return the writeConcern
     * @mongodb.server.release 3.2
     */
    public WriteConcern getWriteConcern() {
    	return null;
    }

    /**
     * Sets the writeConcern
     *
     * @param writeConcern the writeConcern
     * @return this
     * @mongodb.server.release 3.2
     */
    public FindAndModifyOptions writeConcern(final WriteConcern writeConcern) {
        return this;
    }

    /**
     * Returns the collation options
     *
     * @return the collation options
     * @mongodb.server.release 3.4
     */
    public Collation getCollation() {
    	return null;
    }

    /**
     * Sets the collation
     *
     * @param collation the collation
     * @return this
     * @mongodb.server.release 3.4
     */
    public FindAndModifyOptions collation(final Collation collation) {
        return this;
    }
}
