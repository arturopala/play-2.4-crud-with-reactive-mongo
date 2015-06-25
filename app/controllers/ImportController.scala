package controllers

import java.util.UUID
import models.Vessel
import services.VesselsService
import play.api.mvc.{ Action, Controller }
import play.api.libs.json._
import play.api.Play
import play.api.Play.current
import java.io.FileInputStream
import play.api.Logger

class ImportController(vesselsService: VesselsService) extends Controller {

  val nameMap = Map(
    "Official Number" -> "officialNumber",
    "Vessel Name" -> "name",
    "Port of Registry" -> "portOfRegistry",
    "Status" -> "status",
    "Former Vessel Name" -> "formerVesselName",
    "IMO Number" -> "IMONumber",
    "Hull Number" -> "hull",
    "Year Built" -> "yearBuilt",
    "Year Rebuilt" -> "yearRebuilt",
    "Registry Date" -> "registryDate",
    "Certificate Expires" -> "certificateExpires",
    "Number of Encumbrances" -> "numberOfEncumbrances",
    "Vessel Type" -> "vesselType",
    "Gross Tonnage (t)" -> "grossTonnage",
    "Net Tonnage (t)" -> "netTonnage",
    "Construction Type" -> "constructionType",
    "Construction Material" -> "constructionMaterial",
    "Vessel Length (m)" -> "length",
    "Vessel Breadth (m)" -> "width",
    "Vessel Depth (m)" -> "draft",
    "Engine Description" -> "engineDescription",
    "Number of Engines" -> "numberOfEngines",
    "Propulsion Type" -> "propulsionType",
    "Speed (knots)" -> "speed",
    "Propulsion Method" -> "propulsionMethod",
    "Propulsion Power" -> "propulsionPower",
    "Unit of Power" -> "unitOfPower",
    "Builder Name" -> "builderName",
    "Builder Address 1" -> "builderAddress1",
    "Builder Address 2" -> "builderAddress2",
    "Builder Address 3" -> "builderAddress3",
    "Builder City" -> "builderCity",
    "Builder Prov. Code" -> "builderProvinceCode",
    "Builder State / Province" -> "builderState",
    "Builder Country Code" -> "builderCountryCode",
    "Builder Country" -> "builderCountry",
    "Builder Postal Code" -> "builderPostalCode"
  )

  def importDatabase = Action {
    val registrySource = Play.getFile("public/vesselsregistry.json")
    val registryJson = Json.parse(new FileInputStream(registrySource))
    val result = registryJson match {
      case JsArray(values) => Json.arr(
        for (v <- values) yield {
          var obj = Json.obj()
          v match {
            case JsArray(attributes) =>
              for (attr <- attributes) {
                attr match {
                  case JsObject(fields) =>
                    val name = fields("Name").as[JsString].value
                    val literal = fields("Value").as[JsObject].value("Literal").as[JsString].value
                    val vtype = fields("Value").as[JsObject].value("Type").as[JsString].value
                    obj = obj + (nameMap(name) -> (vtype match {
                      case "System.String" => JsString(literal)
                      case "System.Decimal" => JsNumber(new java.math.BigDecimal(literal))
                    }))
                }
              }
          }
          obj
        })
    }
    Ok(result)
  }

}

