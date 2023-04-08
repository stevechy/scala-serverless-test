package com.slopezerosolutions.cardz.model

import monocle.macros.GenLens

object Enemy {
  val health = GenLens[Enemy](_.health)
}
case class Enemy(id: String, health: Int)
