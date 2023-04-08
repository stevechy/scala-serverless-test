package com.slopezerosolutions.dombuilder

trait EventAdapter {
  def textInputAdapter(handler: (String) => Unit): (Any => Unit) = {
    (any: Any) => ()
  }

  def clickAdapter(handler: () => Unit): (Any => Unit) = {
    (any: Any) => ()
  }
}
