package org.kibanaLoadTest

import com.typesafe.config.Config
import org.kibanaLoadTest.helpers.Helper

class ESConfiguration {
  var username = ""
  var password = ""
  var host = ""

  def this(config: Config) {
    this()
    if (
      !config.hasPathOrNull("host")
      || !config.hasPathOrNull("username")
      || !config.hasPathOrNull("password")
    ) {
      throw new RuntimeException(
        "Incorrect configuration - required values:\n" +
          "'host' should be a valid ES host with protocol & port, e.g. 'http://localhost:9200'\n" +
          "'username' and 'password' should be valid credentials"
      )
    }
    this.host = Helper.validateUrl(
      config.getString("host"),
      s"'host' should be a valid ES URL"
    )
    this.username = config.getString("username")
    this.password = config.getString("password")
  }

}
