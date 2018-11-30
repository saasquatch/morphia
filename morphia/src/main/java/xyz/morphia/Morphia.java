/*
 * Copyright (C) 2010 Olafur Gauti Gudmundsson
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */


package xyz.morphia;


import com.mongodb.MongoClient;
import xyz.morphia.internal.DatastoreImpl;

/**
 * The entry point to using Morphia.
 */
public final class Morphia {

    private Morphia() {}

    /**
     * Creates a Datastore
     *
     * @param dbName the name of the database
     * @return a Datastore that you can use to interact with MongoDB
     */
    public static Datastore createDatastore(final String dbName) {
        return createDatastore(new MongoClient(), dbName);
    }

    /**
     * It is best to use a Mongo singleton instance here.
     *
     * @param dbName the name of the database
     * @param mongoClient the client to use
     * @return a Datastore that you can use to interact with MongoDB
     */
    public static Datastore createDatastore(final MongoClient mongoClient, final String dbName) {
        return new DatastoreImpl(mongoClient, dbName);
    }
}
