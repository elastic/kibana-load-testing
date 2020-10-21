# kibana-load-testing

**Pre-conditions**

Start ES and Kibana

**How to run simulation against local instance**

You need to update Kibana/ES configuration in /resources/config/local.conf file
```
mvn install
mvn gatling:test
```

**How to run simulation against cloud instance**

You need to add a new .conf file with valid configuration, e.g. cloud-7.9.2.conf
```
mvn install
export env=cloud-7.9.2 && mvn gatling:test -Dgatling.simulationClass=org.kibanaLoadTest.simulation.<MySimulation>
```

**How to ingest test results**

By default ingestion is disabled. To ingest tests results, set env variable:
```
export ingest=true
```
