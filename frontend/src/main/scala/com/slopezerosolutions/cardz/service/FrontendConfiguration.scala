package com.slopezerosolutions.cardz.service

class FrontendConfiguration {

  def apiUrl(path: String): String = {
    val baseUrl = scalajs.js.Dynamic.global.apiBaseUrl.asInstanceOf[String]
    s"""${baseUrl}/${path}"""
  }
}
