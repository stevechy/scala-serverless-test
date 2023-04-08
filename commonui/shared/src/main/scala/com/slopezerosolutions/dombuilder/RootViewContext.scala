package com.slopezerosolutions.dombuilder

import monocle.macros.GenLens
import monocle.Iso

class RootViewContext[S](var rootContext: S) {
  private var subscribers: Vector[(RootViewContext[S])=>Unit] = Vector()
  def updateContext(update: S => S): Unit = {
    rootContext = update(rootContext)
    publish()
  }

  def publish(): Unit = {
    for (subscriber <- subscribers) {
      subscriber.apply(this)
    }
  }

  def viewContext: ViewContext[S, S] = {
    ViewContext(global = rootContext,
      local = rootContext,
      updateGlobal = updateContext,
      updateLocal = updateContext)
  }

  def subscribe(subscriber: RootViewContext[S] => Unit): Unit = {
   subscribers = subscribers :+ subscriber
  }
}
