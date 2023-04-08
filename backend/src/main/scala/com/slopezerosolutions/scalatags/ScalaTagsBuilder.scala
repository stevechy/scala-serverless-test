package com.slopezerosolutions.scalatags

import com.slopezerosolutions.dombuilder.{DomAttributes, DomBuilder}
import scalatags.Text.all.*

import scala.collection.mutable.ArrayBuffer

class ScalaTagsBuilder extends DomBuilder[ConcreteHtmlTag[String]] {

  def div(domAttributes: DomAttributes, contents: String | List[ConcreteHtmlTag[String]]): ConcreteHtmlTag[String] = {
    contents match {
      case text: String => scalatags.Text.all.div(modifiers(domAttributes): _*)(text)
      case list: List[ConcreteHtmlTag[String]] => scalatags.Text.all.div(modifiers(domAttributes): _*)(list)
    }
  }

  def button(domAttributes: DomAttributes, contents: String | List[ConcreteHtmlTag[String]]): ConcreteHtmlTag[String] = {
    contents match {
      case text: String => scalatags.Text.all.button(modifiers(domAttributes): _*)(text)
      case list: List[ConcreteHtmlTag[String]] => scalatags.Text.all.button(modifiers(domAttributes): _*)(list)
    }
  }

  override def input(domAttributes: DomAttributes): ConcreteHtmlTag[String] = {
    scalatags.Text.all.input(modifiers(domAttributes): _*)
  }

  private def modifiers(domAttributes: DomAttributes): Array[Modifier] = {
    val modifiers = ArrayBuffer[Modifier]()
    for((key,value) <- domAttributes.props) {
      modifiers += (attr(key) := value)
    }
    for ((key, value) <- domAttributes.attributes) {
      modifiers += (attr(key) := value)
    }
    modifiers.toArray
  }
}
