PUT _ingest/pipeline/integration-test-gatling-data-pipeline
{
  "description": "For integration testing.\nDrops responseHeaders and responseBody if ctx.status is not 'KO', sets @timestamp from timestamp.",
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
