package org.dom4j.tree

import org.dom4j._

import scala.beans.BeanProperty

class ConcreteAttribute(qname: QName, var value: String)
  extends AbstractNode with Attribute with WithParent with WithData {

  override def getNodeType: Short = Node.ATTRIBUTE_NODE

  def getQName: QName = qname

  def getValue: String = value
  def setValue(value: String): Unit = this.value = value

  def setNamespace(namespace: Namespace): Unit = {
    val msg = "This Attribute is read only and cannot be changed"
    throw new UnsupportedOperationException(msg)
  }

  override def getText: String = getValue
  override def setText(text: String): Unit = setValue(text)

  override def toString: String = {
    super.toString + " [Attribute: name " + getQualifiedName +
      " value \"" +
      getValue +
      "\"]"
  }

  def accept(visitor: Visitor): Unit =
    visitor.visit(this)

  def getNamespace       = getQName.getNamespace
  def getNamespacePrefix = getQName.getNamespacePrefix
  def getNamespaceURI    = getQName.getNamespaceURI

  override def getName   = getQName.getName
  def getQualifiedName   = getQName.getQualifiedName
}
