package com.slopezerosolutions.cardz.ui.components

import com.slopezerosolutions.cardz.*
import com.slopezerosolutions.cardz.context.AppContext
import com.slopezerosolutions.cardz.model.{AttackCard, Card, Enemy, HealCard}
import com.slopezerosolutions.dombuilder.{DomAttributes, DomBuilder, ViewContext}
import monocle.{Lens, Prism, Traversal}
import monocle.macros.GenLens

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.chaining.*

object GameView {
  case class Context(health: Int,
                     cards: Vector[Card],
                     enemies: Vector[Enemy] = Vector(),
                     enemiesKilled: Int = 0,
                     text: String = "Placeholder",
                     message: String = "Hi",
                     selectedCard: Option[Card] = None) extends AppSection.Context

  val gameContext = Prism.partial[AppSection.Context, Context] { case c: Context => c }(identity)
  val text = GenLens[Context](_.text)
  val selectedCard = GenLens[Context](_.selectedCard)
  val cards = GenLens[Context](_.cards)
  val health = GenLens[Context](_.health)
  val eachEnemy = GenLens[Context](_.enemies).andThen(Traversal.fromTraverse[Vector, Enemy])


  def createNewGame(appContext: AppContext): Context = {
    val cards = Vector[Card](
      AttackCard(appContext.uuidGenerator()),
      HealCard(appContext.uuidGenerator()),
      AttackCard(appContext.uuidGenerator()),
      HealCard(appContext.uuidGenerator()),
      AttackCard(appContext.uuidGenerator()),
    )
    val enemies = Vector[Enemy](
      Enemy(appContext.uuidGenerator(), 10),
      Enemy(appContext.uuidGenerator(), 5)
    )
    Context(health = 20, cards = cards, enemies = enemies)
  }

  def resolveKilledMonsters(context: Context): Context = {
    val killedEnemies = context.enemies.filter(_.health <= 0)
    context.copy(
      enemies = context.enemies.filterNot(_.health <= 0),
      enemiesKilled = context.enemiesKilled + killedEnemies.length
    )
  }

  def selectCard(card: Card): Context => Context = {
    selectedCard.replace(Some(card))
  }

  def playCard(card: Card): Context => Context = {
    selectedCard.replace(None).andThen(cards.modify(_.filterNot(_.id == card.id)))
  }

  def attackEnemy(enemy: Enemy, card: AttackCard): Context => Context = {
    eachEnemy.modify((otherEnemy) => if otherEnemy.id == enemy.id
    then Enemy.health.modify(_ - card.attackPoints)(otherEnemy)
    else otherEnemy)
  }

  def isEmptyAfterPlaying(card: Card, context: Context): Boolean = {
    context.cards.filterNot(_.id == card.id).isEmpty
  }

  def mainView[T](context: ViewContext[AppContext, Context])(using domBuilder: DomBuilder[T]): T = {
    import domBuilder._
    val local = context.local
    val global = context.global

    div(List(
      div(DomAttributes(
        attributes = Map("class" -> "row")),
        List(
          div(DomAttributes(attributes = Map("class" -> "col bd-dark")),
            List(
              div("Game view"),
              div(local.enemies.toList.map((enemy) => {
                EnemyComponent.mainView(enemy,
                  local.selectedCard match {
                    case Some(card@AttackCard(id, attackPoints)) => button(
                      DomAttributes(attributes = Map("class" -> "button error"),
                        handlers = Map("click" -> (_ -> {
                          context.update(
                            playCard(card)
                              .andThen(attackEnemy(enemy, card))
                              .andThen(resolveKilledMonsters)
                          )
                          println(global.gameServiceOption)
                          if (isEmptyAfterPlaying(card, local)) {
                            global.gameServiceOption.map( gameService => {
                              gameService.drawCards().map {
                                case Some(drawnCards) => context.update(cards.modify(_ ++ drawnCards))
                                case None => ()
                              }
                            })
                          }
                        }))
                      ),
                      "Attack")
                    case _ => div("")
                  }
                )
              }))
            )
          ),
          div(DomAttributes(attributes = Map("class" -> "col bd-primary")), List(
            div(List(
              div("Your name is"),
              global.playerName match {
                case Some(name) => div(name)
                case None => div("not entered")
              }
            )),
            div(s"""Health: ${local.health}"""),
            local.selectedCard match {
              case Some(card: HealCard) => button(DomAttributes(attributes = Map("class" -> "button primary",
                "type" -> "button"),
                handlers = Map("click" -> (_ => {
                  context.update(playCard(card).andThen(health.modify(_ + card.healPoints)))
                }))
              ),
                "Heal")
              case _ => div("")
            },
            div(s"Enemies killed: ${local.enemiesKilled}")
          ))
        )),
      div(DomAttributes(attributes = Map("class" -> "row")),
        List(
          div(DomAttributes(attributes = Map("class" -> "col bd-dark")),
            local.cards.toList.filterNot(local.selectedCard.isDefined && _.id == local.selectedCard.get.id).map((card) =>
              GameCard.mainView(card, _ => {
                context.update(selectCard(card))
              }))),
          div(DomAttributes(attributes = Map("class" -> "col")),
            List(local.selectedCard match {
              case Some(card) => GameCard.mainView(card, _ => {
                context.update(selectedCard.replace(None))
              })
              case None => div("Select a card")
            }))
        ))
    )
    )
  }
}
