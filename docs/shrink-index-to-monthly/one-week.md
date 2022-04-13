# Fix Too Large Index - One Weeks worth of Requests

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