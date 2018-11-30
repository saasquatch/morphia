/*
 * Copyright 2016 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.morphia.internal;

import xyz.morphia.annotations.IndexOptions;
import xyz.morphia.annotations.Indexed;
import xyz.morphia.utils.IndexType;

public class IndexedBuilder extends AnnotationBuilder<Indexed> implements Indexed {
    @Override
    public Class<Indexed> annotationType() {
        return Indexed.class;
    }

    @Override
    public IndexOptions options() {
        return get("options");
    }

    @Override
    public IndexType value() {
        return get("value");
    }

    public IndexedBuilder options(final IndexOptions options) {
        put("options", options);
        return this;
    }

    IndexedBuilder name(final String name) {
        put("name", name);
        return this;
    }

    public IndexedBuilder value(final IndexType value) {
        put("value", value);
        return this;
    }

}
