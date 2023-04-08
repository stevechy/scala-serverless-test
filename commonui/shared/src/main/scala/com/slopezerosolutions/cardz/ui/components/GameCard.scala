package com.slopezerosolutions.cardz.ui.components

import com.slopezerosolutions.cardz.model.{AttackCard, Card, HealCard}
import com.slopezerosolutions.dombuilder.{DomAttributes, DomBuilder}

object GameCard {

  def mainView[T](card: Card,
                  cardClickHandler: Any => Unit = _ => ())(using domBuilder: DomBuilder[T]): T = {
    import domBuilder._

    card match {
      case attackCard: AttackCard => div(
        DomAttributes(attributes = Map("class" -> "card bd-dark"),
          handlers = Map("click" -> cardClickHandler)),
        List(
          div(s"Attack card: ${attackCard.id}"),
          div(s"Attack points: ${attackCard.attackPoints}")
        )
      )
      case healCard: HealCard => div(
        DomAttributes(attributes = Map("class" -> "card bd-dark"),
          handlers = Map("click" -> cardClickHandler)),
        List(div(s"Heal card: ${healCard.id}"),
        div(s"Adds ${healCard.healPoints} points of health"))
      )
    }
  }
}
