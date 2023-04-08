package com.slopezerosolutions.cardz.ui.components

import com.slopezerosolutions.cardz.model.Enemy
import com.slopezerosolutions.dombuilder.{DomAttributes, DomBuilder}

object EnemyComponent {
  def mainView[T](enemy: Enemy, children: T*)(using domBuilder: DomBuilder[T]): T = {
    import domBuilder.*

    div(DomAttributes(attributes = Map("class" -> "card")),
      List(
        div(s"Enemy ${enemy.id}"),
        div(s"Health: ${enemy.health}"),
        div(children.toList)
      ))
  }
}
