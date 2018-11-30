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

import xyz.morphia.annotations.Field;
import xyz.morphia.annotations.Index;
import xyz.morphia.annotations.IndexOptions;
import xyz.morphia.utils.IndexType;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public class IndexBuilder extends AnnotationBuilder<Index> implements Index {
    public IndexBuilder() {
    }

    IndexBuilder(final Index original) {
        super(original);
    }

    @Override
    public Class<Index> annotationType() {
        return Index.class;
    }

    public IndexBuilder fields(final String fields) {
        return fields(parseFieldsString(fields));
    }

    @Override
    public Field[] fields() {
        return get("fields");
    }

    @Override
    public IndexOptions options() {
        return get("options");
    }

    private List<Field> parseFieldsString(final String str) {
        List<Field> fields = new ArrayList<>();
        final String[] parts = str.split(",");
        for (String s : parts) {
            s = s.trim();
            IndexType dir = IndexType.ASC;

            if (s.startsWith("-")) {
                dir = IndexType.DESC;
                s = s.substring(1).trim();
            }

            fields.add(new FieldBuilder()
                           .value(s)
                           .type(dir));
        }
        return fields;
    }

    IndexBuilder fields(final List<Field> fields) {
        put("fields", fields.toArray(new Field[0]));
        return this;
    }

    public IndexBuilder fields(final Field... fields) {
        put("fields", fields);
        return this;
    }

    /**
     * Options to apply to the index.  Use of this field will ignore any of the deprecated options defined on {@link Index} directly.
     */
    public IndexBuilder options(final IndexOptions options) {
        put("options", options);
        return this;
    }

}
