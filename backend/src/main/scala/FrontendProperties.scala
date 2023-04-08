import scalatags.Text.all.s

import java.util.Properties
import scala.util.Using

class FrontendProperties {
  private val properties  = new Properties();
  Using(getClass.getResourceAsStream("frontend/frontend.properties")) { stream =>
    properties.load(stream)
  }

  def assetsBaseUrl: String = {
    sys.env.get("ASSETS_BASE_URL").get
  }

  def apiBaseUrl: String = {
    sys.env.get("API_BASE_URL").get
  }

  def entryPointUrl: String = {
    val entrypoint = properties.getProperty("frontend.javascript.entrypoint")
    val entryPointUrl = s"""${assetsBaseUrl}/${entrypoint}"""
    entryPointUrl
  }
}
