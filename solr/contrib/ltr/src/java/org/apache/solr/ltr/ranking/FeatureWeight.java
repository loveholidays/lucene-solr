package org.apache.solr.ltr.ranking;

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
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Weight;
import org.apache.solr.ltr.feature.norm.Normalizer;
import org.apache.solr.ltr.feature.norm.impl.IdentityNormalizer;
import org.apache.solr.ltr.util.MacroExpander;
import org.apache.solr.ltr.util.NamedParams;
import org.apache.solr.request.SolrQueryRequest;

public abstract class FeatureWeight extends Weight {

  final protected String name;
  final protected NamedParams params;
  final protected Normalizer norm;
  final protected IndexSearcher searcher;
  final protected SolrQueryRequest request;
  final protected Map<String,String> efi;
  final protected MacroExpander macroExpander;
  final protected Query originalQuery;
  final protected int id;

  /**
   * Initialize a feature without the normalizer from the feature file. This is
   * called on initial construction since multiple models share the same
   * features, but have different normalizers. A concrete model's feature is
   * copied through featForNewModel().
   *
   * @param q
   *          Solr query associated with this FeatureWeight
   * @param searcher
   *          Solr searcher available for features if they need them
   * @param name
   *          Name of the feature
   * @param params
   *          Custom parameters that the feature may use
   * @param norm
   *          Feature normalizer used to normalize the feature value
   * @param id
   *          Unique ID for this feature. Similar to feature name, except it can
   *          be used to directly access the feature in the global list of
   *          features.
   */
  public FeatureWeight(Query q, IndexSearcher searcher, String name,
      NamedParams params, Normalizer norm, int id, SolrQueryRequest request, Query originalQuery, Map<String,String> efi) {
    super(q);
    this.searcher = searcher;
    this.name = name;
    this.params = params;
    this.id = id;
    this.norm = norm;
    this.request = request;
    this.originalQuery = originalQuery;
    this.efi = efi;
    macroExpander = new MacroExpander(efi);
  }

  public String getName() {
    return name;
  }

  public Normalizer getNorm() {
    return norm;
  }

  public NamedParams getParams() {
    return params;
  }

  public int getId() {
    return id;
  }

  public float getDefaultValue() {
    return 0;
  }

  @Override
  public abstract FeatureScorer scorer(LeafReaderContext context)
      throws IOException;

  @Override
  public Explanation explain(LeafReaderContext context, int doc)
      throws IOException {
    final FeatureScorer r = scorer(context);
    float score = getDefaultValue();
    if (r != null) {
      r.iterator().advance(doc);
      if (r.docID() == doc) score = r.score();
      return Explanation.match(score, r.toString());
    }else{
      return Explanation.match(score, "The feature has no value");
    }
  }

  @Override
  public float getValueForNormalization() throws IOException {
    return 1f;
  }

  @Override
  public void normalize(float norm, float topLevelBoost) {
    // For advanced features that use Solr weights internally, you must override
    // and pass this call on to them
  }

  @Override
  public void extractTerms(Set<Term> terms) {
    // needs to be implemented by query subclasses
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return this.getClass().getName() + " [name=" + name + ", params=" + params
        + "]";
  }

}
