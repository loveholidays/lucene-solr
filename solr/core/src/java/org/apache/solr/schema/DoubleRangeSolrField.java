package org.apache.solr.schema;

/*
 *   Copyright (c) 2017 Lemur Consulting Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

import java.io.IOException;

import org.apache.lucene.document.DoubleRangeField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SortField;
import org.apache.solr.response.TextResponseWriter;
import org.apache.solr.search.QParser;

public class DoubleRangeSolrField extends FieldType {

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
        if (dims[i].contains(" TO ") == false) {
          double p = parseEndPoint(dims[i]);
          min[i] = p;
          max[i] = p;
        }
        else {
          String[] endpoints = dims[i].split(" TO ");
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
  protected IndexableField createField(String name, String val, org.apache.lucene.document.FieldType type, float boost) {
    ParsedRange range = new ParsedRange(val);
    return new DoubleRangeField(name, range.min, range.max);
  }

  @Override
  public org.apache.solr.uninverting.UninvertingReader.Type getUninversionType(SchemaField sf) {
    return null;
  }

  @Override
  public Query getFieldQuery(QParser parser, SchemaField field, String externalVal) {
    return containsQuery(field.getName(), externalVal);
  }

  public static Query containsQuery(String field, String input) {
    ParsedRange range = new ParsedRange(input);
    return DoubleRangeField.newContainsQuery(field, range.min, range.max);
  }

  @Override
  public void write(TextResponseWriter textResponseWriter, String s, IndexableField indexableField) throws IOException {

  }

  @Override
  public SortField getSortField(SchemaField schemaField, boolean reverse) {
    throw new UnsupportedOperationException("Cannot sort by range field");
  }
}
