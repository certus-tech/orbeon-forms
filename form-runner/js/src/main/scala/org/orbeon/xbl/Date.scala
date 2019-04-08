/**
 * Copyright (C) 2018 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.xbl

import org.orbeon.date.JSDateUtils
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.xforms.{$, DocumentAPI, Page}
import org.orbeon.xforms.facade.{Document, XBL, XBLCompanion}
import org.scalajs.jquery.JQuery

import scala.scalajs.js
import DatePickerFacade._
import org.scalajs.dom
import scala.util.control.NonFatal

object Date {
  XBL.declareCompanion("fr|date", new DateCompanion())
}

private class DateCompanion extends XBLCompanion {

  def inputEl    : JQuery     = $(containerElem).find("input").first()
  def iOS        : Boolean    = $(dom.document.body).hasClass("xforms-ios")
  var datePicker : DatePicker = null

  override def init(): Unit = {

    // Add `readonly` attribute on the input if the control is readonly
    val isReadonly = $(containerElem).is(".xforms-readonly")
    xformsUpdateReadonly(isReadonly)

    if (iOS) {
      // On iOS, use native date picker
      inputEl.attr("type", "date")
      inputEl.on("change", () ⇒ onChangeDate())
    } else {
      // Initialize bootstrap-datepicker
      val options              = new DatePickerOptions
      options.autoclose        = true
      options.enableOnReadonly = false
      options.assumeNearbyYear = true
      options.showOnFocus      = false
      options.forceParse       = false
      options.language         = Page.getLang()
      datePicker = inputEl.parent().datepicker(options)
      // Register listeners
      inputEl.on("blur", () ⇒ onBlur())
      datePicker.onChangeDate(() ⇒ onChangeDate())
      Page.onLangChange { newLang ⇒
        datePicker.options.language = newLang
        datePicker.update()
      }
    }
  }

  override def xformsGetValue(): String =
    if (iOS) {
      inputEl.prop("value").asInstanceOf[String]
    } else {
      Option(datePicker.getDate) match {
        case Some(date) ⇒ JSDateUtils.dateToISOStringUsingLocalTimezone(date) // https://github.com/orbeon/orbeon-forms/issues/3907
        case None       ⇒ inputEl.prop("value").asInstanceOf[String]
      }
    }

  override def xformsUpdateValue(newValue: String): Unit =
    if (xformsGetValue() != newValue) {
      if (iOS) {
        inputEl.prop("value", newValue)
      } else {
        if (newValue.isBlank) {
          datePicker.clearDates()
        } else {
          try {
            datePicker.setDate(JSDateUtils.isoDateToStringUsingLocalTimezone(newValue))
          } catch {
            case NonFatal(_) ⇒ // Ignore values we can't parse as dates
          }
        }
      }
    }

  override def xformsUpdateReadonly(readonly: Boolean): Unit =
    inputEl.prop("disabled", readonly)

  override def xformsFocus(): Unit =
    inputEl.focus()

  def setFormat(format: String): Unit = {
    // On iOS, ignore the format as the native widget uses its own format
    if (! iOS) {
      val jsDate = datePicker.getDate
      // Orbeon Forms format:         https://doc.orbeon.com/configuration/properties/xforms#for-xf-input
      // bootstrap-datepicker format: https://bootstrap-datepicker.readthedocs.io/en/latest/options.html#format
      datePicker.options.format = format
        .replaceAllLiterally("[D]"  , "d"   )
        .replaceAllLiterally("[D01]", "dd"  )
        .replaceAllLiterally("[M]"  , "m"   )
        .replaceAllLiterally("[M01]", "mm"  )
        .replaceAllLiterally("[Y]"  , "yyyy")
      datePicker.setDate(jsDate)
    }
  }

  def setExcludedDates(excludedDates: String): Unit = {
    datePicker.options.datesDisabled =
      excludedDates
        .splitTo[js.Array]()
        .map(new js.Date(_))
    datePicker.update()
  }

  def onChangeDate(): Unit = {
    val newValue = xformsGetValue()
    DocumentAPI.setValue(containerElem.id, newValue)
  }

  // On blur, update the field value if we have a valid date, say so the value goes from `1/2` to `1/2/2019`
  def onBlur(): Unit =
    Option(datePicker.getDate).foreach(datePicker.setDate(_))
}

private object DatePickerFacade {

  implicit def jQuery2DatePicker(jQuery: JQuery): JQueryDatePicker =
      jQuery.asInstanceOf[JQueryDatePicker]

  @js.native
  trait JQueryDatePicker extends JQuery {
    def datepicker(options: DatePickerOptions): DatePicker = js.native
  }

  @js.native
  trait DatePicker extends JQuery {
    def on(eventName: String, f: js.Function0[Unit]): Unit = js.native
    def datepicker(methodName: String, args: js.Any*): js.Any = js.native
  }

  class DatePickerOptions extends js.Object {
    var format           : String            = "mm/dd/yyyy"
    var autoclose        : Boolean           = false
    var enableOnReadonly : Boolean           = true
    var assumeNearbyYear : Boolean           = false
    var showOnFocus      : Boolean           = true
    var forceParse       : Boolean           = true
    var datesDisabled    : js.Array[js.Date] = null
    var language         : String            = "en"
  }

  implicit class DatePickerOps(val datePicker: DatePicker) extends AnyVal {
    def onChangeDate(f: js.Function0[Unit]) : Unit              = datePicker.on("changeDate", f)
    def getDate                             : js.Date           = datePicker.datepicker("getDate").asInstanceOf[js.Date]
    def setDate(date: js.Date)              : Unit              = datePicker.datepicker("setDate", date)
    def clearDates()                        : Unit              = datePicker.datepicker("clearDates", Nil)
    def update()                            : Unit              = datePicker.datepicker("update")
    def options                             : DatePickerOptions = datePicker.data("datepicker").o.asInstanceOf[DatePickerOptions]
  }
}
