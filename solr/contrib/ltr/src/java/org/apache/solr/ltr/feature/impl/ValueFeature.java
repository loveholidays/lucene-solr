package org.apache.solr.ltr.feature.impl;

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

import java.io.IOException;
import java.util.Map;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.solr.ltr.feature.norm.Normalizer;
import org.apache.solr.ltr.ranking.Feature;
import org.apache.solr.ltr.ranking.FeatureScorer;
import org.apache.solr.ltr.ranking.FeatureWeight;
import org.apache.solr.ltr.util.FeatureException;
import org.apache.solr.ltr.util.NamedParams;
import org.apache.solr.request.SolrQueryRequest;

public class ValueFeature extends Feature {
  /** name of the attribute containing the value of this feature **/
  private static final String VALUE_FIELD = "value";
  private static final String REQUIRED_PARAM = "required";

  protected float configValue = -1f;
  protected String configValueStr = null;
  protected boolean required = false;

  public ValueFeature() {}

  @Override
  public void init(String name, NamedParams params, int id)
      throws FeatureException {
    super.init(name, params, id);
    final Object paramRequired = params.get(REQUIRED_PARAM);
    if (paramRequired != null)
      this.required = (boolean) paramRequired;
    final Object paramValue = params.get(VALUE_FIELD);
    if (paramValue == null) {
      throw new FeatureException("Missing the field 'value' in params for "
          + this);
    }

    if (paramValue instanceof String) {
      configValueStr = (String) paramValue;
      if (configValueStr.trim().isEmpty()) {
        throw new FeatureException("Empty field 'value' in params for " + this);
      }
    } else {
      try {
        configValue = NamedParams.convertToFloat(paramValue);
      } catch (final NumberFormatException e) {
        throw new FeatureException("Invalid type for 'value' in params for "
            + this);
      }
    }
  }

  @Override
  public FeatureWeight createWeight(IndexSearcher searcher, boolean needsScores, SolrQueryRequest request, Query originalQuery, Map<String,String> efi)
      throws IOException {
    return new ValueFeatureWeight(searcher, name, params, norm, id, request, originalQuery, efi);
  }

  public class ValueFeatureWeight extends FeatureWeight {

    final protected Float featureValue;

    public ValueFeatureWeight(IndexSearcher searcher, String name,
        NamedParams params, Normalizer norm, int id, SolrQueryRequest request, Query originalQuery, Map<String,String> efi) {
      super(ValueFeature.this, searcher, name, params, norm, id, request, originalQuery, efi);
      if (configValueStr != null) {
        final String expandedValue = macroExpander.expand(configValueStr);
        if (expandedValue != null) {
          featureValue = Float.parseFloat(expandedValue);
        } else if (required) {
          throw new FeatureException(this.getClass().getCanonicalName() + " requires efi parameter that was not passed in request.");
        } else {
          featureValue=null;
        }


      } else {
        featureValue = configValue;
      }
    }

    @Override
    public FeatureScorer scorer(LeafReaderContext context) throws IOException {
      if(featureValue!=null)
        return new ValueFeatureScorer(this, featureValue, "ValueFeature");
      else
        return null;
    }

    /**
     * Default FeatureScorer class that returns the score passed in. Can be used
     * as a simple ValueFeature, or to return a default scorer in case an
     * underlying feature's scorer is null.
     */
    public class ValueFeatureScorer extends FeatureScorer {

      float constScore;
      String featureType;
      DocIdSetIterator itr;

      public ValueFeatureScorer(FeatureWeight weight, float constScore,
          String featureType) {
        super(weight);
        this.constScore = constScore;
        this.featureType = featureType;
        itr = new MatchAllIterator();
      }

      @Override
      public float score() {
        return constScore;
      }

      @Override
      public String toString() {
        return featureType + " [name=" + name + " value=" + constScore + "]";
      }

      @Override
      public int docID() {
        return itr.docID();
      }

      @Override
      public DocIdSetIterator iterator() {
        return itr;
      }

    }

  }

}
