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

import com.mongodb.client.model.CollationAlternate;
import com.mongodb.client.model.CollationCaseFirst;
import com.mongodb.client.model.CollationMaxVariable;
import com.mongodb.client.model.CollationStrength;
import xyz.morphia.annotations.Collation;

public class CollationBuilder extends AnnotationBuilder<Collation> implements Collation {
    @Override
    public Class<Collation> annotationType() {
        return Collation.class;
    }

    @Override
    public boolean backwards() {
        return get("backwards");
    }

    @Override
    public boolean caseLevel() {
        return get("caseLevel");
    }

    @Override
    public String locale() {
        return get("locale");
    }

    @Override
    public boolean normalization() {
        return get("normalization");
    }

    @Override
    public boolean numericOrdering() {
        return get("numericOrdering");
    }

    @Override
    public CollationAlternate alternate() {
        return get("alternate");
    }

    @Override
    public CollationCaseFirst caseFirst() {
        return get("caseFirst");
    }

    @Override
    public CollationMaxVariable maxVariable() {
        return get("maxVariable");
    }

    @Override
    public CollationStrength strength() {
        return get("strength");
    }

    public CollationBuilder backwards(final boolean backwards) {
        put("backwards", backwards);
        return this;
    }

    public CollationBuilder caseLevel(final boolean caseLevel) {
        put("caseLevel", caseLevel);
        return this;
    }

    public CollationBuilder locale(final String locale) {
        put("locale", locale);
        return this;
    }

    public CollationBuilder normalization(final boolean normalization) {
        put("normalization", normalization);
        return this;
    }

    public CollationBuilder numericOrdering(final boolean numericOrdering) {
        put("numericOrdering", numericOrdering);
        return this;
    }

    public CollationBuilder alternate(final CollationAlternate alternate) {
        put("alternate", alternate);
        return this;
    }

    public CollationBuilder caseFirst(final CollationCaseFirst caseFirst) {
        put("caseFirst", caseFirst);
        return this;
    }

    public CollationBuilder maxVariable(final CollationMaxVariable maxVariable) {
        put("maxVariable", maxVariable);
        return this;
    }

    public CollationBuilder strength(final CollationStrength strength) {
        put("strength", strength);
        return this;
    }
}
