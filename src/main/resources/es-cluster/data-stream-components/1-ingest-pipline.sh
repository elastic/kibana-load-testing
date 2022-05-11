PUT _ingest/pipeline/gatling-data-pipeline
{
  "description": "Drops responseHeaders and responseBody if ctx.status is not 'KO', sets @timestamp from timestamp.",
  "processors": [
    {
      "set": {
        "field": "responseHeaders",
        "value": "",
        "if": "ctx.status != 'KO'"
      }
    },
    {
      "set": {
        "field": "responseBody",
        "value": "",
        "if": "ctx.status != 'KO'"
      }
    },
    {
      "set": {
        "field": "@timestamp",
        "copy_from": "timestamp"
      }
    }
  ]
}
