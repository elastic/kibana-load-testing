# kibana-load-testing
## Environment requirements
- Maven 3.3.9+
- Java (JDK) 8+

# Running performance testing on CI
Kibana CI has [dedicated jobs](docs/KIBANA_CI.md) to run performance testing for your Kibana branch or Cloud snapshot.


# Running performance testing on your machine
## Running simulation against a local instance
- Start ES and Kibana instances.

<b>Important</b>: Run Kibana without base path or add a static one to your kibana.yml like `server.basePath: "/xfh"` before start.
- Update Kibana configuration in /resources/config/local.conf file
```
app {
  host = "http://localhost:5620" //base url
  // host = "http://localhost:5620/xhf" if you start Kibana with static base path
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
mvn clean test-compile // if you made any changes to the config or simulations
mvn gatling:test -Dgatling.simulationClass=org.kibanaLoadTest.simulation.DemoJourney // could be any other existing simulation class
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
- start test scenario with specified env argument
```
mvn clean test-compile
mvn gatling:test -Denv=config/cloud-7.10.0.conf -Dgatling.simulationClass=org.kibanaLoadTest.simulation.DemoJourney
```

## Running simulation against newly created cloud deployment
- Generate API_KEY for your cloud user account
- Check deployment template at `resources/config/deploy/default.conf`
- start test scenario, new deployment will be created before simulation and deleted after it is finished
```
mvn clean test-compile
export API_KEY=<your_cloud_key>
mvn gatling:test -DcloudStackVersion=7.11.0-SNAPSHOT -Dgatling.simulationClass=org.kibanaLoadTest.simulation.DemoJourney
```
- Optionally create a custom deployment configuration and pass it in command `-DdeploymentConfig=config/deploy/custom.conf`



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

In order to run your simulation, use the following command:
```
mvn gatling:test -Dgatling.simulationClass=org.kibanaLoadTest.simulation.MySimulation
```

## Test results
Gatling generates html report for each simulation run, available in `<project_root>/target/gatling/<simulation>`path

Open `index.html` in browser to preview the report.

Open `testRun.txt` to find more about Kibana instance you tested.

## Running performance testing from VM

Follow [guide](docs/VM_SETUP.md) to setup VM and run tests on it.

# Delete your deployments on Elastic cloud
Run the following command to delete all existing deployments
```
export API_KEY=<your_key>
mvn exec:java -Dexec.mainClass=org.kibanaLoadTest.deploy.DeleteAll -Dexec.classpathScope=test -Dscope=all
```

If you don't provide `-Dscope=all` it will delete only the ones with `load-testing` name prefix

