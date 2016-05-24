package org.dom4j

trait Document extends Branch {

  def getRootElement: Element
  def setRootElement(rootElement: Element): Unit

  def addComment(comment: String): Document
  def addProcessingInstruction(target: String, text: String): Document
}
