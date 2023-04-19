import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.events
import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.slopezerosolutions.cardz.context.AppContext
import com.slopezerosolutions.cardz.ui.components.{AppSection, GameView, NameEntry}
import com.slopezerosolutions.cardz.*
import com.slopezerosolutions.cardz.model.{AttackCard, Card, HealCard}
import com.slopezerosolutions.dombuilder.{DomBuilder, EventAdapter, RootViewContext}
import com.slopezerosolutions.scalatags.ScalaTagsBuilder
import scalatags.Text.all.*

import scala.jdk.CollectionConverters.MapHasAsJava
import java.io.InputStream
import scala.util.{Random, Using}
import scala.io.Source
import org.crac.Resource
import org.crac.Core
import scalatags.Text
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

object LambdaHandler {
  private val frontendProperties = new FrontendProperties()
}
class LambdaHandler extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent], Resource {

  Core.getGlobalContext.register(this)

  override def handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent = {
    if (input.getPath == "/game" && input.getHttpMethod == "GET") {
      return gamePage
    } else if (input.getPath == "/cards/draw" && input.getHttpMethod == "POST") {
      return drawCards()
    }
    notFoundPage(input.getPath)
  }

  private def gamePage = {
    given domBuilder: DomBuilder[Text.TypedTag[String]] = new ScalaTagsBuilder()

    val htmlOutput = html(
      head(
        script(raw(s"""var apiBaseUrl="${LambdaHandler.frontendProperties.apiBaseUrl}";""")),
        script(attr("type") := "module", src := entryPointUrl),
        link(rel := "stylesheet", href := "https://unpkg.com/chota@latest")
      ),
      body(
        style := "padding: 10px;",
        div(cls := "row bg-light")(
          div(cls := "col",
            h2("Server side rendering"),
            AppSection.mainView(RootViewContext(defaultContext.copy(
              viewContext = GameView.Context(health = 22,
                cards = Vector(AttackCard(java.util.UUID.randomUUID().toString)))
            )).viewContext)
          )
        ),
        div(cls := "row")(
          div(cls := "col")(
            h2("Client rendering"),
            div(id := "snabbdom-container",
              AppSection.mainView(
                RootViewContext(defaultContext.copy(viewContext = NameEntry.Context()))
                  .viewContext))
          )
        )
      )
    ).toString
    val event = new APIGatewayProxyResponseEvent()
      .withStatusCode(200)
      .withHeaders(Map("content-type" -> "text/html").asJava)
      .withBody(htmlOutput)
    event
  }

  private def drawCards() = {
    val random = new Random()
    val cards = (1 to 5).map(
      _ => {
        random.nextInt(2) match {
          case 0 => AttackCard(java.util.UUID.randomUUID().toString, random.nextInt(5) + 1)
          case 1 => HealCard(java.util.UUID.randomUUID().toString, random.nextInt(3) + 1)
        }
      }
    )
    val event = new APIGatewayProxyResponseEvent()
      .withStatusCode(200)
      .withHeaders(Map("content-type" -> "application/json").asJava)
      .withBody(cards.asJson.toString)
    event
  }

  private val defaultContext = {
    AppContext(new EventAdapter {},
      AppSection.defaultContext,
      () => java.util.UUID.randomUUID().toString
    )
  }

  private def entryPointUrl = {
    val entryPointUrl = LambdaHandler.frontendProperties.entryPointUrl
    entryPointUrl
  }

  private def notFoundPage(path: String) = {
    val htmlOutput = html(
      head(
        link(rel := "stylesheet", href := "https://unpkg.com/chota@latest")
      ),
      body(
        h1(s"Path not found ${path}"),
        div(cls := "row")(
          div(cls := "col")(
            h2("")
          )
        )
      )
    ).toString
    val event = new APIGatewayProxyResponseEvent()
      .withStatusCode(404)
      .withHeaders(Map("content-type" -> "text/html").asJava)
      .withBody(htmlOutput)
    event
  }

  override def afterRestore(context: org.crac.Context[? <: org.crac.Resource]): Unit = {

  }

  override def beforeCheckpoint(context: org.crac.Context[? <: org.crac.Resource]): Unit = {
    System.out.println("beforeCheckpoint")
    gamePage
    ()
  }

}
