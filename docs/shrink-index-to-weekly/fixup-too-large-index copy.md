# Fix Too Large Index

## Split Large Index into Monthly indices

### Add index template
Settings of note:
 - high priority _should_ override any data stream 
   - Most likely we'll need to remove this template or lower it's priority when we go to data streams via ilm policy for gatling data.
 - only one shard and no replicas 
   - _the indices created from this should be small (monthly) and read operations are not a priority_
```
PUT /_index_template/template_with_gatling_data_mappings_1shard_0replicas
{
  "index_patterns": [
    "gatling-data-*"
  ],
  "priority": 600,
  "version": 1,
  "_meta": {
    "description": "One runtime and several explicit mappings for gatling data."
  },
  "template": {
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 0
    },
    "mappings": {
      "runtime": {
        "path": {
          "type": "keyword",
          "script": {
            "source": "emit(doc['url.keyword'].value.replace(doc['baseUrl.keyword'].value, ''))",
            "lang": "painless"
          }
        }
      },
      "properties": {
        "CI_BUILD_ID": {
          "type": "integer",
          "coerce": true
        },
        "CI_BUILD_URL": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "CI_RUN_URL": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "baseUrl": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "branch": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "buildHash": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "buildNumber": {
          "type": "long"
        },
        "deploymentId": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "esBuildDate": {
          "type": "date"
        },
        "esBuildHash": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "esLuceneVersion": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "esUrl": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "esVersion": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "isCloudDeployment": {
          "type": "boolean"
        },
        "isSnapshotBuild": {
          "type": "boolean"
        },
        "kibanaBranch": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "maxUsers": {
          "type": "long"
        },
        "message": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "method": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "name": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "requestBody": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "requestHeaders": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "requestSendStartTime": {
          "type": "date"
        },
        "requestTime": {
          "type": "long"
        },
        "responseBody": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "responseHeaders": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "responseReceiveEndTime": {
          "type": "date"
        },
        "responseStatus": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "scenario": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "status": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "timestamp": {
          "type": "date"
        },
        "url": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "userId": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "version": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        }
      }
    }
  }
}
```

## Agg by week
```
# 2021-11 by week
GET gatling-data-2021-11/_search
{
  "size": 0, 
  "query": {
    "range": {
      "timestamp": {
        "gte": "2021-11-01T00:00:00.000Z",
        "lte": "2021-12-01T00:00:00.000Z"
      }
    }
  },
  "aggs": {
    "daily": {
      "date_histogram": {
        "field": "timestamp",
        "calendar_interval": "week"
      }
    }
  }
}
```
Result
```
{
  "aggregations" : {
    "daily" : {
      "buckets" : [
        {
          "key_as_string" : "2021-11-01T00:00:00.000Z",
          "key" : 1635724800000,
          "doc_count" : 1632826
        },
        {
          "key_as_string" : "2021-11-08T00:00:00.000Z",
          "key" : 1636329600000,
          "doc_count" : 2832849
        },
        {
          "key_as_string" : "2021-11-15T00:00:00.000Z",
          "key" : 1636934400000,
          "doc_count" : 2531402
        },
        {
          "key_as_string" : "2021-11-22T00:00:00.000Z",
          "key" : 1637539200000,
          "doc_count" : 2105303
        },
        {
          "key_as_string" : "2021-11-29T00:00:00.000Z",
          "key" : 1638144000000,
          "doc_count" : 481740
        }
      ]
    }
  }
}
```

## By Week Re-index and Delete
