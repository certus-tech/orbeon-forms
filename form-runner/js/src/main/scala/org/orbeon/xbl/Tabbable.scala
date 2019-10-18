/**
  * Copyright (C) 2018 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.orbeon.xbl.DndRepeat._
import org.orbeon.xforms
import org.orbeon.xforms.$
import org.orbeon.xforms.facade.{AjaxServer, XBL, XBLCompanion}
import org.scalajs.dom.html
import org.scalajs.dom.html.Element
import org.scalajs.jquery.{JQuery, JQueryEventObject}

import scala.scalajs.js
import scala.scalajs.js.UndefOr

object Tabbable {

  val ActiveClass                  = "active"

  val ExcludeRepeatClassesSelector = ":not(.xforms-repeat-delimiter):not(.xforms-repeat-begin-end):not(.xforms-repeat-template):not(.fr-tabbable-add)"
  val NavTabsSelector              = ".nav-tabs"
  val TabPaneSelector              = ".tab-pane"
  val TabContentSelector           = ".tab-content"
  val ActiveSelector               = s".$ActiveClass"

  XBL.declareCompanion(
    "fr|tabbable",
    new XBLCompanion {

      case class DragState(
        currentDragStartPrev     : Element,
        currentDragStartPosition : Int
      )

      private var dragState : Option[DragState] = None
      private var drake     : Option[Drake]     = None

      override def init(): Unit = {

        if ($(containerElem).is(".fr-tabbable-dnd")) {

          val firstRepeatContainer = $(containerElem).find(NavTabsSelector)(0)

          val newDrake =
            Dragula(
              js.Array(firstRepeatContainer),
              new DragulaOptions {

                override val mirrorContainer: UndefOr[Element] = firstRepeatContainer

                // Only elements in drake.containers will be taken into account
                override def isContainer(el: Element) = false

                override def moves(el: Element, source: Element, handle: Element, sibling: Element) = {
                  val jEl = $(el)
                  (
                      jEl.prev().is(IsRepeatDelimiterSelector) ||
                      jEl.prev().prev().is(IsRepeatDelimiterSelector)
                  ) &&
                      jEl.is(IsDndMovesSelector)
                }

                override def accepts(el: Element, target: Element, source: Element, sibling: Element) =
                  sibling != null && $(sibling).is(IsNotGuSelector)
              }
            )

          newDrake.onDrag((el: Element, source: Element) ⇒ {

            val jEl = $(el)

            dragState = Some(
              DragState(
                currentDragStartPrev     = jEl.prev()(0),
                currentDragStartPosition = jEl.prevAll(IsDndMovesSelector + ExcludeRepeatClassesSelector).length
              )
            )
          })

          newDrake.onDragend((el: Element) ⇒ {
            dragState = None
          })

          // This is almost identical in `DndRepeat`. Should remove code duplication.
          newDrake.onDrop((el: Element, target: Element, source: Element, sibling: Element) ⇒ {
            dragState foreach { dragState ⇒

              val jEl = $(el)

              val dndEnd = jEl.prevAll(IsDndMovesSelector + ExcludeRepeatClassesSelector).length

              val repeatId = jEl.prevAll(IsRepeatBeginEndSelector).attr("id").get.substring("repeat-begin-".length)

              val beforeEl = dragState.currentDragStartPrev
              val dndStart = dragState.currentDragStartPosition

              if (dndStart != dndEnd) {

                lazy val moveBack: js.Function = () ⇒ {
                  $(beforeEl).after(el)
                  // TODO: Fix this if we switch to `jquery-facade`
                  AjaxServer.ajaxResponseReceived.asInstanceOf[js.Dynamic].remove(moveBack)
                }

                // Restore order once we get an Ajax response back
                // NOTE: You might think that we should wait for the specific response to the Ajax request corresponding to
                // the event below. However, we should move the element back to its original location before *any*
                // subsequent Ajax response is processed, because it might touch parts of the DOM which have been moved. So
                // doing this is probably the right thing to do.
                AjaxServer.ajaxResponseReceived.add(moveBack)

                // Thinking this should instead block input, but only after a while show a modal screen.
                // ORBEON.util.Utils.displayModalProgressPanel(ORBEON.xforms.Controls.getForm(companion.container).id)

                 xforms.DocumentAPI.dispatchEvent(
                  new js.Object {
                    val targetId  = repeatId
                    val eventName = "xxforms-dnd"
                    val properties = new js.Object {
                      val `dnd-start` = dndStart + 1
                      val `dnd-end`   = dndEnd + 1
                    }
                  }
                )
              }
            }
          })
        }

        // Select first tab
        // https://github.com/orbeon/orbeon-forms/issues/3458
        selectTab(0)

        // 2016-10-13: We use our own logic to show/hide tabs based on position as we want to be able to
        // support dynamically repeated tabs.
        $(containerElem).on("click.tabbable.data-api", "[data-toggle = 'tabbable']", {
          (bound: html.Element, e: JQueryEventObject) ⇒ {

            e.preventDefault()  // don"t allow anchor navigation
            e.stopPropagation() // prevent ancestor tab handlers from running

            val newLi = $(bound).parent(ExcludeRepeatClassesSelector)

            if (! newLi.is(ActiveSelector)) {
              val tabPosition = newLi.prevAll(ExcludeRepeatClassesSelector).length
              selectTab(tabPosition)
            }
          }
        }: js.ThisFunction)
      }

      override def destroy(): Unit = {
        drake foreach (_.destroy())
        drake = None
      }

      def findAllTabPanes: JQuery =
        $(containerElem).children().children(TabContentSelector).children(TabPaneSelector + ExcludeRepeatClassesSelector)

      // Called from XBL component
      def selectTabForPane(targetElem: html.Element): Unit = {

        val allTabPanes      = findAllTabPanes
        val ancestorTabPanes = $(targetElem).parents(TabPaneSelector)
        val intersectionPane = allTabPanes.filter(ancestorTabPanes)

        val index = allTabPanes.index(intersectionPane)
        if (index < 0)
            return

        selectTab(index)
      }

      // Called from XBL component
      def selectTab(tabPosition: Int): Unit = {

        if (tabPosition < 0)
            return

        val allLis = $(containerElem).find("> div > .nav-tabs").children(ExcludeRepeatClassesSelector)
        if (tabPosition > allLis.length - 1)
            return

        val newLi = $(allLis(tabPosition))
        if (newLi.is(ActiveSelector))
            return

        val allTabPanes = newLi.closest(NavTabsSelector).nextAll().children(TabPaneSelector).filter(ExcludeRepeatClassesSelector)
        val newTabPane  = allTabPanes(tabPosition)

        allLis.removeClass(ActiveClass)
        allTabPanes.removeClass(ActiveClass)

        newLi.addClass(ActiveClass)
        $(newTabPane).addClass(ActiveClass)
      }
    }
  )
}
