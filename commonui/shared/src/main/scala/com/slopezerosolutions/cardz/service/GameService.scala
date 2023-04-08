package com.slopezerosolutions.cardz.service

import com.slopezerosolutions.cardz.model.Card

import scala.concurrent.Future

trait GameService {
  def drawCards(): Future[Option[Vector[Card]]]
}
