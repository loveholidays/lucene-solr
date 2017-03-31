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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.document.DoubleRangeField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SortField;
import org.apache.solr.response.TextResponseWriter;
import org.apache.solr.search.QParser;

public class RangeField extends FieldType {

  static class ParsedRange {

    final double[] min;
    final double[] max;

    ParsedRange(String rangeInput) {
      // input looks like this: [4 TO 8, 7, -14.3 TO MAX, MIN TO MAX]
      rangeInput = rangeInput.substring(1, rangeInput.length() - 1);  // strip []
      String[] dims = rangeInput.split(",");
      if (dims.length > 4)
        throw new IllegalArgumentException("Cannot index more than 4 dimensions");
      min = new double[dims.length];
      max = new double[dims.length];
      for (int i = 0; i < dims.length; i++) {
        String dim = dims[i].trim();
        if (dim.contains(" TO ") == false) {
          double p = parseEndPoint(dim);
          min[i] = p;
          max[i] = p;
        }
        else {
          String[] endpoints = dim.split(" TO ");
          min[i] = parseEndPoint(endpoints[0]);
          max[i] = parseEndPoint(endpoints[1]);
        }
      }
    }

    static double parseEndPoint(String in) {
      if ("MIN".equals(in))
        return Double.MIN_VALUE;
      if ("MAX".equals(in))
        return Double.MAX_VALUE;
      return Double.parseDouble(in);
    }
  }

  @Override
  public List<IndexableField> createFields(SchemaField field, Object value, float boost) {
    ParsedRange range = new ParsedRange(value.toString());
    IndexableField f = new DoubleRangeField(field.name, range.min, range.max);
    if (field.stored() == false)
      return Collections.singletonList(f);

    List<IndexableField> fields = new ArrayList<>();
    fields.add(f);
    fields.add(new StoredField(field.name, value.toString()));
    return fields;
  }

  @Override
  public org.apache.solr.uninverting.UninvertingReader.Type getUninversionType(SchemaField sf) {
    return null;
  }

  @Override
  public Query getFieldQuery(QParser parser, SchemaField field, String externalVal) {
    throw new UnsupportedOperationException("You must use the {!pr} queryparser with range fields");
  }

  public Query containsQuery(String field, String input) {
    ParsedRange range = new ParsedRange(input);
    return DoubleRangeField.newContainsQuery(field, range.min, range.max);
  }

  public Query withinQuery(String field, String input) {
    ParsedRange range = new ParsedRange(input);
    return DoubleRangeField.newWithinQuery(field, range.min, range.max);
  }

  public Query intersectsQuery(String field, String input) {
    ParsedRange range = new ParsedRange(input);
    return DoubleRangeField.newIntersectsQuery(field, range.min, range.max);
  }

  @Override
  public void write(TextResponseWriter textResponseWriter, String s, IndexableField indexableField) throws IOException {
    textResponseWriter.writeVal(s, indexableField.stringValue());
  }

  @Override
  public SortField getSortField(SchemaField schemaField, boolean reverse) {
    throw new UnsupportedOperationException("Cannot sort by range field");
  }
}
