package org.apache.solr.ltr.ranking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Explanation;
import org.apache.solr.ltr.feature.LTRScoringAlgorithm;
import org.apache.solr.ltr.util.ModelException;
import org.apache.solr.ltr.util.NamedParams;

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

public abstract class EuristicBoostedModel extends LTRScoringAlgorithm {
  public static final String EURISTIC_BOOST_PARAM = "boost";
  public static final String ADDITIVE_BOOST = "additive";
  public static final String MULTIPLICATIVE_BOOST = "multiplicative";


  protected EuristicFeatureBoost euristicFeatureBoost;
  protected LTRScoringAlgorithm LTRModel;

  protected float internalModelScore;
  protected float boostFeatureValue;
  protected float weightedBoostFeatureValue;


  class EuristicFeatureBoost {
    public static final String FEATURE_NAME = "feature";
    public static final String WEIGHT = "weight";
    public static final String BOOST_TYPE = "type";

    public String feature;
    public int featureIndex;
    public float weight;
    public String type;

    public EuristicFeatureBoost(Map<String, Object> featureBoostParam) {
      checkParams(featureBoostParam);

      feature = ((String) featureBoostParam.get(FEATURE_NAME));
      weight = NamedParams.convertToFloat(featureBoostParam.get(WEIGHT));
      featureIndex = getFeatureIndex(feature);
      type = MULTIPLICATIVE_BOOST;
      if (featureBoostParam.get(BOOST_TYPE) != null) {
        type = (String) featureBoostParam.get(BOOST_TYPE);
      }
    }

    private void checkParams(Map<String, Object> euristicBoostParams) {
      checkParam(euristicBoostParams, FEATURE_NAME);
      checkParam(euristicBoostParams, WEIGHT);
    }

    private void checkParam(Map<String, Object> featureBoostParams, String paramName) {
      String modelName=EuristicBoostedModel.this.getName();
      if (!featureBoostParams.containsKey(paramName)) {
        throw new ModelException("Model " + modelName + " required param " + paramName + " not defined");
      }
    }

    private int getFeatureIndex( String featureName) {
      String modelName=EuristicBoostedModel.this.getName();
      final List<Feature> features = getFeatures();
      Optional<Feature> featureToBoostOptional = features.stream()
          .filter(f -> f.getName().equals(featureName))
          .findFirst();

      if (featureToBoostOptional.isPresent()) {
        Feature featureToBoost = featureToBoostOptional.get();
        return featureToBoost.getId();
      } else {
        throw new ModelException("Model " + modelName + " doesn't contain any feature with the name=[" + feature + "}");
      }
    }
  }

  public EuristicBoostedModel(String name, List<Feature> features,
                              String featureStoreName, List<Feature> allFeatures,
                              NamedParams params) throws ModelException {
    super(name, features, featureStoreName, allFeatures, params);
    initEuristicBoost();
  }

  protected void initEuristicBoost() {
    Map<String, Object> params = (Map<String, Object>) EuristicBoostedModel.this.getParams().get(EURISTIC_BOOST_PARAM);
    if (params != null) {
      this.euristicFeatureBoost = new EuristicFeatureBoost(params);
    }
  }

  @Override
  public float score(float[] modelFeatureValuesNormalized) {
    internalModelScore = LTRModel.score(modelFeatureValuesNormalized);
    float finalScore = internalModelScore;
    if (euristicFeatureBoost != null) {
      boostFeatureValue = modelFeatureValuesNormalized[euristicFeatureBoost.featureIndex];
      weightedBoostFeatureValue = euristicFeatureBoost.weight * boostFeatureValue;

      if (MULTIPLICATIVE_BOOST.equals(euristicFeatureBoost.type)) {
        if (internalModelScore < 0) {
          weightedBoostFeatureValue = 1 / weightedBoostFeatureValue;
        }
        finalScore *= weightedBoostFeatureValue;
      } else if (ADDITIVE_BOOST.equals(euristicFeatureBoost.type)) {
        finalScore += weightedBoostFeatureValue;
      }

    }
    return finalScore;
  }

  @Override
  public Explanation explain(LeafReaderContext context, int doc, float finalScore, List<Explanation> featureExplanations) {
    if (euristicFeatureBoost == null) {
      return LTRModel.explain(context, doc, internalModelScore, featureExplanations);
    }

    String boostTypeOperator = "prod";
    if (euristicFeatureBoost.type.equals(ADDITIVE_BOOST))
      boostTypeOperator = "sum";

    final List<Explanation> details = new ArrayList<>();
    final Explanation boostExplain = Explanation.match(weightedBoostFeatureValue,
        euristicFeatureBoost.weight + " weight on feature [" + euristicFeatureBoost.feature + "] : " + boostFeatureValue);
    details.add(boostExplain);
    final Explanation internalModelExplain = LTRModel.explain(context, doc, internalModelScore, featureExplanations);
    details.add(internalModelExplain);

    return Explanation.match(finalScore, toString()
        + " model applied to features, " + boostTypeOperator + " of:", details);
  }
}
