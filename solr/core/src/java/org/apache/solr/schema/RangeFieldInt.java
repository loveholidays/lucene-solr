/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.document.IntRangeField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;

public class RangeFieldInt extends RangeField {

  static class ParsedRange {

    final int[] min;
    final int[] max;

    ParsedRange(String rangeInput) {
      // input looks like this: [4 TO 8, 7, -14.3 TO MAX, MIN TO MAX]
      rangeInput = rangeInput.substring(1, rangeInput.length() - 1);  // strip []
      String[] dims = rangeInput.split(",");
      if (dims.length > 4)
        throw new IllegalArgumentException("Cannot index more than 4 dimensions");
      min = new int[dims.length];
      max = new int[dims.length];

      for (int i = 0; i < dims.length; i++) {
        String dim = dims[i].trim();
        if (dim.contains(" TO ")) {
          String[] endpoints = dim.split(" TO ");
          min[i] = parseEndPoint(endpoints[0], Integer.MIN_VALUE);
          max[i] = parseEndPoint(endpoints[1], Integer.MAX_VALUE);
        } else {
          int p = Integer.parseInt(dim);
          min[i] = p;
          max[i] = p;
        }
      }
    }

    static int parseEndPoint(String in, int def) {
      switch (in) {
        case "MIN":
          return Integer.MIN_VALUE;
        case "MAX":
          return Integer.MAX_VALUE;
        case "*":
          return def;
      }
      return Integer.parseInt(in);
    }
  }

  @Override
  public List<IndexableField> createFields(SchemaField field, Object value, float boost) {
    String rangeInput = value.toString();

    ParsedRange range = new ParsedRange(rangeInput);
    IndexableField f = new IntRangeField(field.name, range.min, range.max);
    if (field.stored() == false)
      return Collections.singletonList(f);

    List<IndexableField> fields = new ArrayList<>();
    fields.add(f);
    if (field.stored()) {
      fields.add(new StoredField(field.name, rangeInput));
    }
    return fields;
  }

  @Override
  public Query containsQuery(String field, String input) {
    ParsedRange range = new ParsedRange(input);
    return IntRangeField.newContainsQuery(field, range.min, range.max);
  }

  @Override
  public Query withinQuery(String field, String input) {
    ParsedRange range = new ParsedRange(input);
    return IntRangeField.newWithinQuery(field, range.min, range.max);
  }

  @Override
  public Query intersectsQuery(String field, String input) {
    ParsedRange range = new ParsedRange(input);
    return IntRangeField.newIntersectsQuery(field, range.min, range.max);
  }

}
