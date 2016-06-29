Apache Solr Learning to Rank
========

This is the main [learning to rank integrated into solr](http://www.slideshare.net/lucidworks/learning-to-rank-in-solr-presented-by-michael-nilsson-diego-ceccarelli-bloomberg-lp)
repository.
[Read up on learning to rank](https://en.wikipedia.org/wiki/Learning_to_rank)

Apache Solr Learning to Rank (LTR) provides a way for you to extract features
directly inside Solr for use in training a machine learned model.  You can then
deploy that model to Solr and use it to rerank your top X search results.

# Test the plugin with solr/example/techproducts in a few easy steps!

Solr provides some simple example of indices. In order to test the plugin with
the techproducts example please follow these steps.

1. Compile solr and the examples

    `cd solr`
    `ant dist`
    `ant server`

2. Run the example to setup the index

   `./bin/solr -e techproducts`

3. Stop solr and install the plugin:
     1. Stop solr

        `./bin/solr stop`
     2. Create the lib folder

        `mkdir example/techproducts/solr/techproducts/lib`
     3. Install the plugin in the lib folder

        `cp build/contrib/ltr/lucene-ltr-7.0.0-SNAPSHOT.jar example/techproducts/solr/techproducts/lib/`
     4. Replace the original solrconfig with one importing all the ltr components

        `cp contrib/ltr/example/solrconfig.xml example/techproducts/solr/techproducts/conf/`

4. Run the example again

   `./bin/solr -e techproducts`

   Note you could also have just restarted your collection using the admin page.
   You can find more detailed instructions [here](https://wiki.apache.org/solr/SolrPlugins).

5. Deploy features and a model

      `curl -XPUT 'http://localhost:8983/solr/techproducts/schema/feature-store'  --data-binary "@./contrib/ltr/example/techproducts-features.json"  -H 'Content-type:application/json'`

      `curl -XPUT 'http://localhost:8983/solr/techproducts/schema/model-store'  --data-binary "@./contrib/ltr/example/techproducts-model.json"  -H 'Content-type:application/json'`

6. Have fun !

     * Access to the default feature store

       http://localhost:8983/solr/techproducts/schema/feature-store/\_DEFAULT\_
     * Access to the model store

       http://localhost:8983/solr/techproducts/schema/model-store
     * Perform a reranking query using the model, and retrieve the features

       http://localhost:8983/solr/techproducts/query?indent=on&q=test&wt=json&rq={!ltr%20model=svm%20reRankDocs=25%20efi.user_query=%27test%27}&fl=[features],price,score,name


BONUS: Train an actual machine learning model

1. Download and install [liblinear](https://www.csie.ntu.edu.tw/~cjlin/liblinear/)

2. Change `contrib/ltr/scripts/config.json` "trainingLibraryLocation" to point to the train directory where you installed liblinear.

3. Extract features, train a reranking model, and deploy it to Solr.

  `cd  contrib/ltr/scripts`

  `python ltr_generateModel.py -c config.json`

   This script deploys your features from `config.json` "featuresFile" to Solr.  Then it takes the relevance judged query
   document pairs of "userQueriesFile" and merges it with the features extracted from Solr into a training
   file.  That file is used to train a rankSVM model, which is then deployed to Solr for you to rerank results.

4. Search and rerank the results using the trained model

   http://localhost:8983/solr/techproducts/query?indent=on&q=test&wt=json&rq={!ltr%20model=ExampleModel%20reRankDocs=25%20efi.user_query=%27test%27}&fl=price,score,name

# Changes to solrconfig.xml
```xml
<config>
  ...

  <!-- Query parser used to rerank top docs with a provided model -->
  <queryParser name="ltr" class="org.apache.solr.ltr.ranking.LTRQParserPlugin" />

  <!--  Transformer that will encode the document features in the response.
  For each document the transformer will add the features as an extra field
  in the response. The name of the field will be the the name of the
  transformer enclosed between brackets (in this case [features]).
  In order to get the feature vector you will have to
  specify that you want the field (e.g., fl="*,[features])  -->
  <transformer name="features" class="org.apache.solr.ltr.ranking.LTRFeatureLoggerTransformerFactory" />


  <!-- Component that hooks up managed resources for features and models -->
  <searchComponent name="ltrComponent" class="org.apache.solr.ltr.ranking.LTRComponent"/>
  <requestHandler name="/query" class="solr.SearchHandler">
    <lst name="defaults">
      <str name="echoParams">explicit</str>
      <str name="wt">json</str>
      <str name="indent">true</str>
      <str name="df">id</str>
    </lst>
    <arr name="last-components">
      <!-- Use the component in your requestHandler -->
      <str>ltrComponent</str>
    </arr>
  </requestHandler>

  <query>
    ...

    <!-- Cache for storing and fetching feature vectors -->
    <cache name="QUERY_DOC_FV"
      class="solr.search.LRUCache"
      size="4096"
      initialSize="2048"
      autowarmCount="4096"
      regenerator="solr.search.NoOpRegenerator" />
  </query>

</config>

```

# Defining Features
In the learning to rank plugin, you can define features in a feature space
using standard Solr queries. As an example:

###### features.json
```json
[
{ "name": "isBook",
  "type": "org.apache.solr.ltr.feature.impl.SolrFeature",
  "params":{ "fq": ["{!terms f=category}book"] }
},
{
  "name":  "documentRecency",
  "type": "org.apache.solr.ltr.feature.impl.SolrFeature",
  "params": {
      "q": "{!func}recip( ms(NOW,publish_date), 3.16e-11, 1, 1)"
  }
},
{
  "name":"originalScore",
  "type":"org.apache.solr.ltr.feature.impl.OriginalScoreFeature",
  "params":{}
},
{
  "name" : "userTextTitleMatch",
  "type" : "org.apache.solr.ltr.feature.impl.SolrFeature",
  "params" : { "q" : "{!field f=title}${user_text}" }
},
 {
   "name" : "userFromMobile",
   "type" : "org.apache.solr.ltr.feature.impl.ValueFeature",
   "params" : { "value" : ${userFromMobile}, "required":true }
 }
]
```

Defines four features. Anything that is a valid Solr query can be used to define
a feature.

### Filter Query Features
The first feature isBook fires if the term 'book' matches the category field
for the given examined document. Since in this feature q was not specified,
either the score 1 (in case of a match) or the score 0 (in case of no match)
will be returned.

### Query Features
In the second feature (documentRecency) q was specified using a function query.
In this case the score for the feature on a given document is whatever the query
returns (1 for docs dated now, 1/2 for docs dated 1 year ago, 1/3 for docs dated
2 years ago, etc..) . If both an fq and q is used, documents that don't match
the fq will receive a score of 0 for the documentRecency feature, all other
documents will receive the score specified by the query for this feature.

### Original Score Feature
The third feature (originalScore) has no parameters, and uses the
OriginalScoreFeature class instead of the SolrFeature class.  Its purpose is
to simply return the score for the original search request against the current
matching document.

### External Features
Users can specify external information that can to be passed in as
part of the query to the ltr ranking framework. In this case, the
fourth feature (userTextPhraseMatch) will be looking for an external field
called 'user_text' passed in through the request, and will fire if there is
a term match for the document field 'title' from the value of the external
field 'user_text'.  You can provide default values for external features as
well by specifying ${myField:myDefault}, similar to how you would in a Solr config.
In this case, the fifth feature (userFromMobile) will be looking for an external parameter
called 'userFromMobile' passed in through the request, if the ValueFeature is :
required=true, it will throw an exception if the external feature is not passed
required=false, it will silently ignore the feature and avoid the scoring ( at Document scoring time, the model will consider 0 as feature value)
The advantage in defining a feature as not required, where possible, is to avoid wasting caching space and time in calculating the featureScore.
See the [Run a Rerank Query](#run-a-rerank-query) section for how to pass in external information.

### Custom Features
Custom features can be created by extending from
org.apache.solr.ltr.ranking.Feature, however this is generally not recommended.
The majority of features should be possible to create using the methods described
above.

# Defining Models
Currently the Learning to Rank plugin supports 2 main types of
ranking models: [Ranking SVM](http://www.cs.cornell.edu/people/tj/publications/joachims_02c.pdf)
and [LambdaMART](http://research.microsoft.com/pubs/132652/MSR-TR-2010-82.pdf)

### Ranking SVM
Currently only a linear ranking svm is supported. Use LambdaMART for
a non-linear model. If you'd like to introduce a bias set a constant feature
to the bias value you'd like and make a weight of 1.0 for that feature.

###### model.json
```json
{
    "type":"org.apache.solr.ltr.ranking.RankSVMModel",
    "name":"myModelName",
    "features":[
        { "name": "userTextTitleMatch"},
        { "name": "originalScore"},
        { "name": "isBook"}
    ],
    "params":{
        "weights": {
            "userTextTitleMatch": 1.0,
            "originalScore": 0.5,
            "isBook": 0.1
        }

    }
}
```

This is an example of a toy Ranking SVM model. Type specifies the class to be
using to interpret the model (RankSVMModel in the case of Ranking SVM).
Name is the model identifier you will use when making request to the ltr
framework. Features specifies the feature space that you want extracted
when using this model. All features that appear in the model params will
be used for scoring and must appear in the features list.  You can add
extra features to the features list that will be computed but not used in the
model for scoring, which can be useful for logging.
Params are the Ranking SVM parameters.

Good library for training SVM's (https://www.csie.ntu.edu.tw/~cjlin/liblinear/ ,
https://www.csie.ntu.edu.tw/~cjlin/libsvm/) . You will need to convert the
libSVM model format to the format specified above.

### LambdaMART

###### model2.json
```json
{
    "type":"org.apache.solr.ltr.ranking.LambdaMARTModel",
    "name":"lambdamartmodel",
    "features":[
        { "name": "userTextTitleMatch"},
        { "name": "originalScore"}
    ],
    "params":{
        "trees": [
            {
                "weight" : 1,
                "tree": {
                    "feature": "userTextTitleMatch",
                    "threshold": 0.5,
                    "left" : {
                        "value" : -100
                    },
                    "right": {
                        "feature" : "originalScore",
                        "threshold": 10.0,
                        "left" : {
                            "value" : 50
                        },
                        "right" : {
                            "value" : 75
                        }
                    }
                }
            },
            {
                "weight" : 2,
                "tree": {
                    "value" : -10
                }
            }
        ]
    }
}
```
This is an example of a toy LambdaMART. Type specifies the class to be using to
interpret the model (LambdaMARTModel in the case of LambdaMART). Name is the
model identifier you will use when making request to the ltr framework.
Features specifies the feature space that you want extracted when using this
model. All features that appear in the model params will be used for scoring and
must appear in the features list.  You can add extra features to the features
list that will be computed but not used in the model for scoring, which can
be useful for logging. Params are the LambdaMART specific parameters. In this
case we have 2 trees, one with 3 leaf nodes and one with 1 leaf node.

A good library for training LambdaMART ( http://sourceforge.net/p/lemur/wiki/RankLib/ ).
You will need to convert the RankLib model format to the format specified above.

###### model3.json
```json
{
    "type":"org.apache.solr.ltr.ranking.HeuristicBoostedLambdaMARTModel",
    "name":"lambdamartmodel",
    "features":[
        { "name": "userTextTitleMatch"},
        { "name": "originalScoreFeature"}
    ],
    "params":{
    "boost": {
          "feature": "originalScoreFeature",
          "weight": 0.5,
          "type": "SUM"
        },
        "trees": [
            {
                "weight" : 1,
                "tree": {
                    "feature": "userTextTitleMatch",
                    "threshold": 0.5,
                    "left" : {
                        "value" : -100
                    },
                    "right": {
                        "value" : 10
                        
                    }
                }
            },
            {
                "weight" : 2,
                "tree": {
                    "value" : -10
                }
            }
        ]
    }
}
```
This is an example of a toy LambdaMART using an additional capability : Heuristic Boosting.
This capability is currently supported only by the : org.apache.solr.ltr.ranking.HeuristicBoostedLambdaMARTModel .
This possible configuration covers the use cases when you have a feature of the Solr Document that was not available at training time.
e.g.
Your training set is not built on clicks of the search results and contains legacy data, but you want to include the original score as a boosting factor

Let's see the configuration in details :
Given :
"features":[
        { "name": "userTextTitleMatch"},
        { "name": "originalScoreFeature"}
    ]
    
"boost":{
                "feature":"originalScoreFeature",
                "weight":0.1,
                "type":"SUM"
            }
The original score feature value, weighted by a factor of 0.1, will be added to the score produced by the LambdaMART trees.

"boost":{
                "feature":"originalScoreFeature",
                "weight":0.1,
                "type":"PRODUCT"
            }
The original score feature value, weighted by a factor of 0.1, will be multiplied to the score produced by the LambdaMART trees.

N.B. Take extra care when using this approach. This introduces a manual boosting to the score calculation,
which adds flexibility when you don't have much data for training. However, you will loose some of the benefits
of a machine learned model, which was optimized to rerank your results.
As you get more data and your model becomes better, you should shift off the manual boosting.


# Deploy Models and Features
To send features run

`curl -XPUT 'http://localhost:8983/solr/collection1/schema/feature-store' --data-binary @/path/features.json -H 'Content-type:application/json'`

To send models run

`curl -XPUT 'http://localhost:8983/solr/collection1/schema/model-store' --data-binary @/path/model.json -H 'Content-type:application/json'`


# View Models and Features
`curl -XGET 'http://localhost:8983/solr/collection1/schema/feature-store'`

`curl -XGET 'http://localhost:8983/solr/collection1/schema/model-store'`

# Run a Rerank Query
Add to your original solr query
`rq={!ltr model=myModelName reRankDocs=25}`

The model name is the name of the model you sent to solr earlier.
The number of documents you want reranked, which can be larger than the
number you display, is reRankDocs.

### Pass in external information for external features
Add to your original solr query
`rq={!ltr reRankDocs=3 model=externalmodel efi.field1='text1' efi.field2='text2'}`

Where "field1" specifies the name of the customized field to be used by one
or more of your features, and text1 is the information to be pass in. As an
example that matches the earlier shown userTextTitleMatch feature one could do:

`rq={!ltr reRankDocs=3 model=externalmodel efi.user_text='Casablanca' efi.user_intent='movie'}`

# Extract features
To extract features you need to use the feature vector transformer `features`

`fl=*,score,[features]&rq={!ltr model=yourModel reRankDocs=25}`

If you use `[features]` together with your reranking model, it will return
the array of features used by your model. Otherwise you can just ask solr to
produce the features without doing the reranking:

`fl=*,score,[features store=yourFeatureStore]`

This will return the values of the features in the given store.


# Assemble training data
In order to train a learning to rank model you need training data. Training data is
what "teaches" the model what the appropriate weight for each feature is. In general
training data is a collection of queries with associated documents and what their ranking/score
should be. As an example:
```
secretary of state|John Kerry|0.66|CROWDSOURCE
secretary of state|Cesar A. Perales|0.33|CROWDSOURCE
secretary of state|New York State|0.0|CROWDSOURCE
secretary of state|Colorado State University Secretary|0.0|CROWDSOURCE

microsoft ceo|Satya Nadella|1.0|CLICK_LOG
microsoft ceo|Microsoft|0.0|CLICK_LOG
microsoft ceo|State|0.0|CLICK_LOG
microsoft ceo|Secretary|0.0|CLICK_LOG
```
In this example the first column indicates the query, the second column indicates a unique id for that doc,
the third column indicates the relative importance or relevance of that doc, and the fourth column indicates the source.
There are 2 primary ways you might collect data for use with your machine learning algorithim. The first
is to collect the clicks of your users given a specific query. There are many ways of preparing this data
to train a model (http://www.cs.cornell.edu/people/tj/publications/joachims_etal_05a.pdf). The general idea
is that if a user sees multiple documents and clicks the one lower down, that document should be scored higher
than the one above it. The second way is explicitly through a crowdsourcing platform like Mechanical Turk or
CrowdFlower. These platforms allow you to show human workers documents associated with a query and have them
tell you what the correct ranking should be.

At this point you'll need to collect feature vectors for each query document pair. You can use the information
from the Extract features section above to do this. An example script has been included in scripts/ltr_generateModel.py.

# Explanation of the core reranking logic
An LTR model is plugged into the ranking through the [LTRQParserPlugin](/solr/contrib/ltr/src/java/org/apache/solr/ltr/ranking/LTRQParserPlugin.java). The plugin will
read from the request the model, an instance of [LTRScoringAlgorithm](solr/contrib/ltr/src/java/org/apache/solr/ltr/feature/LTRScoringAlgorithm.java),
plus other parameters. The plugin will generate an [LTRQuery](solr/contrib/ltr/src/java/org/apache/solr/ltr/ranking/LTRQuery.java), a particular org.apache.solr.search. RankQuery.
It wraps the original solr query for the first pass ranking, and uses the provided model in a
[ModelQuery](solr/contrib/ltr/src/java/org/apache/solr/ltr/ranking/ModelQuery.java) to
rescore and rerank the top documents.  The ModelQuery will take care of computing the values of all the
[features](solr/contrib/ltr/src/java/org/apache/solr/ltr/ranking/Feature.java) and then will delegate the final score
generation to the LTRScoringAlgorithm.

