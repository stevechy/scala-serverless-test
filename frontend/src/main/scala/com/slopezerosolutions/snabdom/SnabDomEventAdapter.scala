package com.slopezerosolutions.snabdom

import com.slopezerosolutions.dombuilder.EventAdapter
import org.scalajs.dom
import org.scalajs.dom.HTMLInputElement

class SnabDomEventAdapter extends EventAdapter {
  override def textInputAdapter(handler: (String) => Unit): (Any => Unit) = {
    (event) =>
      event match {
        case inputEvent: dom.Event => {
          inputEvent.target match {
            case e: HTMLInputElement => {
              handler(e.value)
            }
          }
          ()
        }
      }
  }

  override def clickAdapter(handler: () => Unit): (Any => Unit) = {
    (event) =>
      event match {
        case inputEvent: dom.MouseEvent => {
          handler()
        }
      }
  }
}
