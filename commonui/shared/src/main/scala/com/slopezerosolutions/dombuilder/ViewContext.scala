package com.slopezerosolutions.dombuilder

import monocle.{Lens, Optional, Prism}

case class ViewContext[R, C](global: R,
                             local: C,
                             updateGlobal: (R => R) => Unit,
                             updateLocal: (C => C) => Unit) {
  def zoomInto[Z](zoom: Lens[C, Z]): ViewContext[R, Z] = {
    val zoomedContext = zoom.get(local)
    def childUpdater(updateChild: (Z => Z)): Unit = {
      updateLocal(zoom.modify(updateChild))
    }
    copy[R,Z](
      local =  zoomedContext,
      updateLocal = childUpdater
    )
  }

  def zoomOptional[Z](zoomOptional: Optional[C,Z], zoomedLocal: Z):  ViewContext[R, Z] = {
    def optionalChildUpdater(updateChild: (Z => Z)): Unit = {
      updateLocal(zoomOptional.modify(updateChild))
    }
    copy[R, Z](
      local = zoomedLocal,
      updateLocal = optionalChildUpdater
    )
  }

  def update(updater: C => C): Unit = {
    updateLocal(updater)
  }
}
