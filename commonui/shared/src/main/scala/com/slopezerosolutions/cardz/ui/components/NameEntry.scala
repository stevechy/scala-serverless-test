package com.slopezerosolutions.cardz.ui.components

import com.slopezerosolutions.cardz.context.AppContext
import com.slopezerosolutions.dombuilder.{DomAttributes, DomBuilder, ViewContext}
import monocle.{Lens, Prism}
import monocle.macros.GenLens

object NameEntry {
  case class Context(playerName: String = "") extends AppSection.Context

  val context = Prism.partial[AppSection.Context, Context] { case c: Context => c }(identity)
  val playerName: Lens[Context, String] = GenLens[Context](_.playerName)

  def mainView[T](context: ViewContext[AppContext, Context])(using domBuilder: DomBuilder[T]): T = {
    import domBuilder._
    val local = context.local
    val global = context.global
    div(List(
      div(List(input(DomAttributes(props = Map("name" -> "playerName", "value" -> local.playerName),
        handlers = Map("change" -> global.eventAdapter.textInputAdapter((input) => {
          context.updateLocal(playerName.modify(_ => input))
        }))
      )))),
      div(List(
        button(DomAttributes(handlers = Map("click" -> global.eventAdapter.clickAdapter(() => {
          context.updateGlobal(AppContext.playerName.modify(_ => Some(local.playerName)))
          context.updateGlobal(AppContext.viewContext.modify(_ => GameView.createNewGame(global)))
        }))),
          "Enter name")))
    ))
  }
}
