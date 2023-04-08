package com.slopezerosolutions.dombuilder


object DomAttributes {
  val empty = DomAttributes()
}
case class DomAttributes(props: Map[String,String] = Map(),
                         attributes: Map[String,String] = Map(),
                         handlers: Map[String, Any => Unit] = Map())
