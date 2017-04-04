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

package org.apache.solr.search;

import org.apache.lucene.search.Query;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.RangeField;

public class PointRangeQParserPlugin extends QParserPlugin {

  public static final String NAME = "pr";

  @Override
  public QParser createParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
    return new QParser(qstr, localParams, params, req) {
      @Override
      public Query parse() throws SyntaxError {
        String field = localParams.required().get("field");
        String method = localParams.required().get("method");

        FieldType ft = req.getSchema().getFieldType(field);

        if (ft instanceof RangeField) {
          return rangeFieldQuery((RangeField)ft, field, method);
        }

        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Unsupported Field Type for field " + field + ": " + ft.getTypeName());
      }

      private Query rangeFieldQuery(RangeField rangeField, String field, String method) {
        switch (method) {
          case "within":
          case "w":
            return rangeField.withinQuery(field, qstr);
          case "contains":
          case "c":
            return rangeField.containsQuery(field, qstr);
          case "intersects":
          case "i":
            return rangeField.intersectsQuery(field, qstr);
          default:
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Unsupported method: " + method);
        }
      }
    };
  }
}
