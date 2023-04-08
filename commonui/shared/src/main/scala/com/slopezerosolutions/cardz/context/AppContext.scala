package com.slopezerosolutions.cardz.context

import com.slopezerosolutions.cardz.service.GameService
import com.slopezerosolutions.cardz.ui.components.AppSection
import com.slopezerosolutions.dombuilder.EventAdapter
import monocle.Lens
import monocle.macros.GenLens

object AppContext {
  val viewContext: Lens[AppContext, AppSection.Context] = GenLens[AppContext](_.viewContext)
  val playerName: Lens[AppContext, Option[String]] = GenLens[AppContext](_.playerName)
}
case class AppContext(eventAdapter: EventAdapter,
                      viewContext: AppSection.Context,
                      uuidGenerator: () => String,
                      playerName: Option[String] = None,
                      gameServiceOption: Option[GameService] = None)


