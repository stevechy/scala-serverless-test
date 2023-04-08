import com.slopezerosolutions.cardz.context.AppContext
import com.slopezerosolutions.cardz.model.Card
import com.slopezerosolutions.cardz.service.{FrontendConfiguration, FrontendGameService}
import com.slopezerosolutions.cardz.ui.components.{AppSection, GameView, NameEntry}
import com.slopezerosolutions.dombuilder.{DomBuilder, RootViewContext}
import com.slopezerosolutions.snabdom.{SnabDomBuilder, SnabDomEventAdapter}
import org.scalajs.dom
import snabbdom.*
import snabbdom.modules.*

import scala.scalajs.js

object Main {
  def main(args: Array[String]): Unit = {
    val container = dom.document.getElementById("snabbdom-container")
    val patch = init(Seq(Attributes.module,
      Classes.module,
      Props.module,
      Styles.module,
      EventListeners.module,
      Dataset.module))


    given domBuilder: DomBuilder[VNode] = new SnabDomBuilder()

    var containerVnode: Option[VNode] = None
    val rootViewContext = RootViewContext(AppContext(new SnabDomEventAdapter(),
      NameEntry.Context(),
      () => js.Dynamic.global.crypto.randomUUID().asInstanceOf[String],
      gameServiceOption = Some(new FrontendGameService(new FrontendConfiguration()))
      ))
    rootViewContext.subscribe((root) => {
      val context = root.viewContext
      val vnodes = h("div",
        VNodeData(props = Map("id" -> "snabbdom-container")),
        Array[VNode](AppSection.mainView(context)))
      containerVnode = containerVnode match {
        case Some(vNode) => {
           Some(patch(vNode, vnodes))
        }
        case None => {
          Some(patch(container, vnodes))
        }
      }

    })
    rootViewContext.publish()
  }
}