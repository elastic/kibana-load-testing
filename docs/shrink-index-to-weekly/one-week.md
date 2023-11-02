# Fix Too Large Index - One Weeks worth of Requests

Add the index template 
```
PUT _index_template/template_with_gatling_data_mappings_1shard_0replicas
{
  "version": 1,
  "priority": 600,
  "template": {
    "settings": {
      "index": {
        "number_of_shards": "1",
        "number_of_replicas": "0"
      }
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
        "maxUsers": {
          "type": "long"
        },
        "responseBody": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "branch": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "buildNumber": {
          "type": "long"
        },
        "esBuildDate": {
          "type": "date"
        },
        "isSnapshotBuild": {
          "type": "boolean"
        },
        "requestBody": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "scenario": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "responseReceiveEndTime": {
          "type": "date"
        },
        "deploymentId": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "esLuceneVersion": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "esVersion": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "timestamp": {
          "type": "date"
        },
        "isCloudDeployment": {
          "type": "boolean"
        },
        "requestSendStartTime": {
          "type": "date"
        },
        "method": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "CI_BUILD_URL": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "buildHash": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "esUrl": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "CI_RUN_URL": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "message": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "responseStatus": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "esBuildHash": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "userId": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "version": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "url": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "requestTime": {
          "type": "long"
        },
        "baseUrl": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "requestHeaders": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "responseHeaders": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "CI_BUILD_ID": {
          "coerce": true,
          "type": "integer"
        },
        "kibanaBranch": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "name": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        },
        "status": {
          "type": "text",
          "fields": {
            "keyword": {
              "ignore_above": 256,
              "type": "keyword"
            }
          }
        }
      }
    }
  },
  "index_patterns": [
    "gatling-data-*"
  ],
  "composed_of": [],
  "_meta": {
    "description": "One runtime and several explicit mappings for gatling data."
  }
}
```
Get the weeks
```
# 2021-11 by week
GET gatling-data-2021-11/_search
{
  "size": 0, 
  "query": {
    "range": {
      "timestamp": {
        "gte": "2021-11-01T00:00:00.000Z",
        "lte": "2021-11-31T00:00:00.000Z"
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

Verify for Week 1
```
# 2021-11 first week
GET gatling-data-2021-11/_search
{
  "size": 0, 
  "query": {
    "range": {
      "timestamp": {
        "gte": "2021-11-01T00:00:00.000Z",
        "lt": "2021-11-08T00:00:00.000Z"
      }
    }
  }
}
```

Reindex to week 1 and delete it
```
POST _reindex?wait_for_completion=false
{
  "source": {
    "index": "gatling-data-2021-11",
    "query": {
      "range" : {
        "timestamp": {
          "gte": "2021-11-01T00:00:00.000Z",
          "lt": "2021-11-08T00:00:00.000Z"
        }
      }
    }
  },
  "dest": {
    "index": "gatling-data-2021-11-week1"
  }
}

# Wait for tasks to complete
GET _tasks/yTA-1FkiT5-w-tV73hU0QA:61200357

# check total count before delete
GET gatling-data-2021-11/_count
# result
{
  "count" : 59938115,
  "_shards" : {
    "total" : 1,
    "successful" : 1,
    "skipped" : 0,
    "failed" : 0
  }
}

POST gatling-data-2021-11/_delete_by_query?wait_for_completion=false
{
  "query": {
    "range": {
      "timestamp": {
        "gte": "2021-11-01T00:00:00.000Z",
        "lt": "2021-11-08T00:00:00.000Z"
      }
    }
  }
}

# check total count after delete
GET gatling-data-2021-11/_count
# result
{
  "count" : 58305289,
  "_shards" : {
    "total" : 1,
    "successful" : 1,
    "skipped" : 0,
    "failed" : 0
  }
}

```