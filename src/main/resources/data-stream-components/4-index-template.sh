PUT _index_template/gatling-data-index-template
{
  "priority": 500,
  "index_patterns": [
    "gatling-data*"
  ],
  "data_stream": {
    "hidden": false
  },
  "composed_of": [
    "gatling-settings-template"
  ],
  "_meta": {
    "description": "Template for gatling time series data"
  }
}
