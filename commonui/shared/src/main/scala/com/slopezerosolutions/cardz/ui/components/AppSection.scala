package com.slopezerosolutions.cardz.ui.components

import com.slopezerosolutions.cardz.context.AppContext
import com.slopezerosolutions.cardz.ui.components.GameView.Context
import com.slopezerosolutions.dombuilder.{DomAttributes, DomBuilder, ViewContext}

object AppSection {
  abstract class Context
  val defaultContext = new Context{}

  def mainView[T](context: ViewContext[AppContext, AppContext])(using domBuilder: DomBuilder[T]): T = {
    import domBuilder._
    val local = context.local
    local.viewContext match {
      case sectionContext: GameView.Context => {
        GameView.mainView(
          context.zoomOptional(AppContext.viewContext.andThen(GameView.gameContext),
            sectionContext)
        )
      }
      case sectionContext: NameEntry.Context => {
        NameEntry.mainView(context.zoomOptional(AppContext.viewContext.andThen(NameEntry.context),
          sectionContext))
      }
      case _ => div(DomAttributes(props = Map("data-error" -> "Unhandled ui context")), List())
    }
  }
}
