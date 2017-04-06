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

import org.apache.lucene.document.ByteRangeField;
import org.apache.lucene.document.IntRangeField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;

public class RangeFieldByte extends RangeField {

  static class ParsedRange {

    final byte[] min;
    final byte[] max;

    ParsedRange(String rangeInput) {
      // input looks like this: [4 TO 8, 7, -14.3 TO MAX, MIN TO MAX]
      rangeInput = rangeInput.substring(1, rangeInput.length() - 1);  // strip []
      String[] dims = rangeInput.split(",");
      if (dims.length > 4)
        throw new IllegalArgumentException("Cannot index more than 4 dimensions");
      min = new byte[dims.length];
      max = new byte[dims.length];

      for (int i = 0; i < dims.length; i++) {
        String dim = dims[i].trim();
        if (dim.contains(" TO ")) {
          String[] endpoints = dim.split(" TO ");
          min[i] = parseEndPoint(endpoints[0], Byte.MIN_VALUE);
          max[i] = parseEndPoint(endpoints[1], Byte.MAX_VALUE);
        } else {
          byte p = Byte.parseByte(dim);
          min[i] = p;
          max[i] = p;
        }
      }
    }

    static byte parseEndPoint(String in, byte def) {
      switch (in) {
        case "MIN":
          return Byte.MIN_VALUE;
        case "MAX":
          return Byte.MAX_VALUE;
        case "*":
          return def;
      }
      return Byte.parseByte(in);
    }
  }

  @Override
  public List<IndexableField> createFields(SchemaField field, Object value, float boost) {
    String rangeInput = value.toString();

    ParsedRange range = new ParsedRange(rangeInput);
    IndexableField f = new ByteRangeField(field.name, range.min, range.max);
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
    return ByteRangeField.newContainsQuery(field, range.min, range.max);
  }

  @Override
  public Query withinQuery(String field, String input) {
    ParsedRange range = new ParsedRange(input);
    return ByteRangeField.newWithinQuery(field, range.min, range.max);
  }

  @Override
  public Query intersectsQuery(String field, String input) {
    ParsedRange range = new ParsedRange(input);
    return ByteRangeField.newIntersectsQuery(field, range.min, range.max);
  }

}
