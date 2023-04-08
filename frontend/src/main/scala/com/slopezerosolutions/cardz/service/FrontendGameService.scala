package com.slopezerosolutions.cardz.service
import com.slopezerosolutions.cardz.model.Card
import org.scalajs.dom
import org.scalajs.dom.{Request, RequestInit}
import org.scalajs.dom.experimental.HttpMethod

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalajs.js.Thenable.Implicits.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*

class FrontendGameService(frontendConfiguration: FrontendConfiguration) extends GameService {
  override def drawCards(): Future[Option[Vector[Card]]] = {
    val request = new Request(frontendConfiguration.apiUrl("cards/draw"),
      new RequestInit {
      method = dom.HttpMethod.POST
    })
    val response: Future[Either[Error, Vector[Card]]] = for {
      response <- dom.fetch(request)
      bodyText <- response.text()
    } yield {
      decode[Vector[Card]](bodyText)
    }
    response.map(_.toOption)
  }
}
