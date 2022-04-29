package org.kibanaLoadTest.simulation

import io.gatling.core.Predef.{rampConcurrentUsers, _}
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef.http

class NGinxSimulation extends Simulation {

  val nGinxScenario: ScenarioBuilder = scenario("Nginx test")
    .exec(
      http("load index page")
        .get("http://192.168.14.3:8080/index.nginx-debian.html")
    )

  setUp(
    // generate a closed workload injection profile
    // ramp users +100 linear every 20 seconds
    nGinxScenario.inject(
      rampConcurrentUsers(0).to(100).during(20),
      rampConcurrentUsers(100).to(200).during(20),
      rampConcurrentUsers(200).to(300).during(20),
      rampConcurrentUsers(300).to(400).during(20),
      rampConcurrentUsers(400).to(500).during(20),
      rampConcurrentUsers(500).to(600).during(20),
      rampConcurrentUsers(600).to(700).during(20),
      rampConcurrentUsers(700).to(800).during(20),
      rampConcurrentUsers(800).to(900).during(20),
      rampConcurrentUsers(900).to(1000).during(20),
      rampConcurrentUsers(1000).to(1100).during(20),
      rampConcurrentUsers(1100).to(1200).during(20),
      rampConcurrentUsers(1200).to(1300).during(20),
      rampConcurrentUsers(1300).to(1400).during(20),
      rampConcurrentUsers(1400).to(1500).during(20),
      rampConcurrentUsers(1500) to(0) during(20)
    )
  )
}
