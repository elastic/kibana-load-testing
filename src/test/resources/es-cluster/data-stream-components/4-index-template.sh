PUT _index_template/integration-test-gatling-data-index-template
{
  "priority": 500,
  "index_patterns": [
    "integration-test-gatling-data*"
  ],
  "data_stream": {
    "hidden": false
  },
  "composed_of": [
    "integration-test-gatling-settings-template"
  ],
  "_meta": {
    "description": "Template for gatling time series data"
  }
}
