package com.slopezerosolutions.cardz.model

abstract sealed class Card {
  def id: String
}
case class AttackCard(id: String, attackPoints: Int = 5) extends Card

case class HealCard(id: String, healPoints: Int = 3) extends Card