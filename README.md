# kibana-load-testing

## Running simulation against a local instance
- Start ES and Kibana instances
- Update Kibana configuration in /resources/config/local.conf file
```
app {
  host = "http://localhost:5620" //base url
  version = "8.0.0" //version
}

security {
  on = true // false for OSS, otherwise - true
}

auth {
  providerType = "basic"
  providerName = "basic"
  username = "elastic" // user should have permissions to load sample data and access plugins
  password = "changeme"
}
```
- start test scenario
```
mvn install
mvn gatling:test -Dgatling.simulationClass=org.kibanaLoadTest.simulation.DemoJourney
```

## Running simulation against existing cloud deployment
- Create Elastic Cloud deployment
- Add a new configuration file, e.g. `config/cloud-7.10.0.conf`
```
app {
  host = "https://str.us-central1.gcp.foundit.no:9243" //base url
  version = "7.10.0"
}

security {
  on = true
}

auth {
  providerType = "basic" // required starting 7.10
  providerName = "cloud-basic" // required starting 7.10
  username = "elastic" // user should have permissions to load sample data and access plugins
  password = "pwd"
}
```
- start test scenario
```
mvn install
export env=config/cloud-7.9.2.conf
mvn gatling:test -Dgatling.simulationClass=org.kibanaLoadTest.simulation.DemoJourney
```

## Running simulation against newly created cloud deployment
- Generate API_KEY for your cloud user account
- (Optional) Change deployment template at `resources/config/deploy/default.conf`
- start test scenario, new deployment will be created before simulation and deleted after it is finished
```
mvn install
export API_KEY=<your_cloud_key>
export cloudDeploy=7.11.0-SNAPSHOT
mvn gatling:test -Dgatling.simulationClass=org.kibanaLoadTest.simulation.DemoJourney
```

Follow logs to track deployment status:

```
09:40:23.535 [INFO ] httpClient - preparePayload: Using Config(SimpleConfigObject({"elasticsearch":{"deployment_template":"gcp-io-optimized","memory":8192},"kibana":{"memory":1024},"version":"7.11.0-SNAPSHOT"}))
09:40:23.593 [INFO ] httpClient - createDeployment: Creating new deployment
09:40:29.848 [INFO ] httpClient - createDeployment: deployment b76dd4a9255a417ca133fe8edd8157a2 is created
09:40:29.848 [INFO ] httpClient - waitForClusterToStart: waitTime 300000ms, poolingInterval 20000ms
09:40:30.727 [INFO ] httpClient - waitForClusterToStart: Deployment is in progress... Map(kibana -> initializing, elasticsearch -> initializing, apm -> initializing)
...
09:46:01.211 [INFO ] httpClient - waitForClusterToStart: Deployment is in progress... Map(kibana -> reconfiguring, elasticsearch -> started, apm -> started)
09:46:21.989 [INFO ] httpClient - waitForClusterToStart: Deployment is ready!
...
...
10:01:08.146 [INFO ] i.g.c.c.Controller - StatsEngineStopped
simulation org.kibanaLoadTest.simulation.DemoJourney completed in 429 seconds
10:01:08.148 [INFO ] httpClient - deleteDeployment: Deployment b76dd4a9255a417ca133fe8edd8157a2
10:01:09.440 [INFO ] httpClient - deleteDeployment: Finished with status code 200
```

## Adding new simulation

The simplest way is to add new class in `simulation` package:
```
class MySimulation extends BaseSimulation {
  val scenarioName = s"My new simulation ${appConfig.buildVersion}"

  val scn = scenario(scenarioName)
    .exec(
      Login
        .doLogin(
          appConfig.isSecurityEnabled,
          appConfig.loginPayload,
          appConfig.loginStatusCode
        )
        .pause(5 seconds)
    )
    // conbine your simulation using existing scenarios or adding new ones
    .exec(Discover.doQuery(appConfig.baseUrl, defaultHeaders).pause(5 seconds))
    .exec(...)
    .exec(...)

  // Define load model, check https://gatling.io/docs/current/general/simulation_setup/
  setUp(
    scn
      .inject(
        rampConcurrentUsers(10) to (250) during (4 minute)
      )
      .protocols(httpProtocol)
  ).maxDuration(10 minutes)
}
```

## Running tests from VM

Follow [guide](VM_SETUP.md) to setup VM and run tests on it

