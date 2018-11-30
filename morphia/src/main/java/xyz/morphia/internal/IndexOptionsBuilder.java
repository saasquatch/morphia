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

import xyz.morphia.annotations.Collation;
import xyz.morphia.annotations.Index;
import xyz.morphia.annotations.IndexOptions;
import xyz.morphia.annotations.Indexed;

@SuppressWarnings("deprecation")
public class IndexOptionsBuilder extends AnnotationBuilder<IndexOptions> implements IndexOptions {
    @Override
    public Class<IndexOptions> annotationType() {
        return IndexOptions.class;
    }

    @Override
    public boolean background() {
        return get("background");
    }

    @Override
    public boolean disableValidation() {
        return get("disableValidation");
    }

    @Override
    public int expireAfterSeconds() {
        return get("expireAfterSeconds");
    }

    @Override
    public String language() {
        return get("language");
    }

    @Override
    public String languageOverride() {
        return get("languageOverride");
    }

    @Override
    public String name() {
        return get("name");
    }

    @Override
    public boolean sparse() {
        return get("sparse");
    }

    @Override
    public boolean unique() {
        return get("unique");
    }

    @Override
    public String partialFilter() {
        return get("partialFilter");
    }

    @Override
    public Collation collation() {
        return get("collation");
    }

    public IndexOptionsBuilder background(final boolean background) {
        put("background", background);
        return this;
    }

    public IndexOptionsBuilder disableValidation(final boolean disableValidation) {
        put("disableValidation", disableValidation);
        return this;
    }

    public IndexOptionsBuilder expireAfterSeconds(final int expireAfterSeconds) {
        put("expireAfterSeconds", expireAfterSeconds);
        return this;
    }

    public IndexOptionsBuilder language(final String language) {
        put("language", language);
        return this;
    }

    public IndexOptionsBuilder languageOverride(final String languageOverride) {
        put("languageOverride", languageOverride);
        return this;
    }

    public IndexOptionsBuilder name(final String name) {
        put("name", name);
        return this;
    }

    public IndexOptionsBuilder sparse(final boolean sparse) {
        put("sparse", sparse);
        return this;
    }

    public IndexOptionsBuilder unique(final boolean unique) {
        put("unique", unique);
        return this;
    }

    public IndexOptionsBuilder partialFilter(final String partialFilter) {
        put("partialFilter", partialFilter);
        return this;
    }

    public IndexOptionsBuilder collation(final Collation collation) {
        put("collation", collation);
        return this;
    }

    IndexOptionsBuilder migrate(final Index index) {
        putAll(toMap(index));
        return this;
    }

    IndexOptionsBuilder migrate(final Indexed index) {
        putAll(toMap(index));
        return this;
    }
}
