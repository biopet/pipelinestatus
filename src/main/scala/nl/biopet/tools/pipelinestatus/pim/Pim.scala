/*
 * Copyright (c) 2017 Biopet
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package nl.biopet.tools.pipelinestatus.pim

import nl.biopet.utils.conversions
import play.api.libs.json._

/**
  * Created by pjvanthof on 17/03/2017.
  */
trait PimClasses {
  def toMap: Map[String, Any]

  def toJson: JsObject = {
    conversions.mapToJson(
      toMap
        .filter {
          case (_, None) => false
          case _         => true
        }
        .map {
          case (key, value: PimClasses)        => key -> value.toJson
          case (key, value: Array[PimClasses]) => key -> value.map(_.toJson)
          case (key, value)                    => key -> value
        })
  }

  override def toString: String = Json.stringify(toJson)
}

case class Run(name: String,
               title: Option[String] = None,
               description: Option[String] = None,
               user: String,
               root: Node,
               links: Array[Link] = Array(),
               statusTypes: Array[StatusType] = Array(),
               assignedTo: Array[String] = Array(),
               customData: Map[String, Any] = Map())
    extends PimClasses {
  def toMap: Map[String, Any] = Map(
    "name" -> name,
    "title" -> title,
    "description" -> description,
    "user" -> user,
    "root" -> root,
    "links" -> links,
    "statusTypes" -> statusTypes,
    "assignedTo" -> assignedTo,
    "customData" -> customData
  )
}

case class Link(fromPort: String,
                toPort: String,
                description: Option[String] = None,
                linkType: Option[String] = None,
                title: Option[String] = None,
                customData: Map[String, Any] = Map())
    extends PimClasses {
  def toMap: Map[String, Any] = Map(
    "fromPort" -> fromPort,
    "toPort" -> toPort,
    "linkType" -> linkType,
    "title" -> title,
    "description" -> description,
    "customData" -> customData
  )
}

case class Node(name: String,
                description: Option[String] = None,
                title: Option[String] = None,
                inPorts: Array[Port] = Array(),
                outPorts: Array[Port] = Array(),
                nodeType: Option[String] = None,
                children: Array[Node] = Array(),
                customData: Map[String, Any] = Map())
    extends PimClasses {
  def toMap: Map[String, Any] = Map(
    "name" -> name,
    "title" -> title,
    "description" -> description,
    "inPorts" -> inPorts,
    "outPorts" -> outPorts,
    "type" -> nodeType,
    "children" -> children,
    "customData" -> customData
  )
}

case class StatusType(description: Option[String] = None,
                      title: Option[String] = None,
                      color: Option[String] = None)
    extends PimClasses {
  def toMap: Map[String, Any] = Map(
    "title" -> title,
    "description" -> description,
    "color" -> color
  )
}

case class Port(name: String,
                description: Option[String] = None,
                title: Option[String] = None,
                dataType: Option[String] = None,
                customData: Map[String, Any] = Map())
    extends PimClasses {
  def toMap: Map[String, Any] = Map(
    "name" -> name,
    "title" -> title,
    "description" -> description,
    "dataType" -> dataType,
    "customData" -> customData
  )
}

case class Job(name: String,
               description: Option[String] = None,
               title: Option[String] = None,
               node: String,
               status: Int,
               customData: Map[String, Any] = Map())
    extends PimClasses {
  def toMap: Map[String, Any] = Map(
    "name" -> name,
    "title" -> title,
    "description" -> description,
    "node" -> node,
    "status" -> status,
    "customData" -> customData
  )
}

object JobStatus extends Enumeration {
  val idle = Value
  val running = Value
  val success = Value
  val failed = Value
}
