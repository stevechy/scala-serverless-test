package com.slopezerosolutions.dombuilder

trait DomBuilder[T] {

  def div(domAttributes: DomAttributes, contents: String | List[T]): T

  def div(contents: String | List[T] ): T = {
    div(DomAttributes.empty, contents)
  }

  def button(domAttributes: DomAttributes, contents: String | List[T]): T

  def input(domAttributes: DomAttributes): T
}
