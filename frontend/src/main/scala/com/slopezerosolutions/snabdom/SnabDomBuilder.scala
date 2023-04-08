package com.slopezerosolutions.snabdom

import com.slopezerosolutions.dombuilder.{DomAttributes, DomBuilder}
import snabbdom.VNode
import snabbdom.*
import org.scalajs.dom

class SnabDomBuilder extends DomBuilder[VNode] {

  override def div(domAttributes: DomAttributes, contents: String | List[VNode]): VNode = {
    contents match {
      case text: String => h("div", vNodeData(domAttributes), text)
      case list: List[VNode] => h("div", vNodeData(domAttributes), list.toArray)
    }
  }

  override def button(domAttributes: DomAttributes, contents: String | List[VNode]): VNode = {
    contents match {
      case text: String => h("button", vNodeData(domAttributes), text)
      case list: List[VNode] => h("button", vNodeData(domAttributes), list.toArray)
    }
  }

  override def input(domAttributes: DomAttributes): VNode = {
    h("input", vNodeData(domAttributes))
  }

  def vNodeData(domAttributes: DomAttributes): VNodeData = {
    VNodeData(
      props = domAttributes.props,
      attrs = domAttributes.attributes,
      on = domAttributes.handlers.transform((event, handler) => {
        handler
      }
      )
    )
  }

}
