package com.youtoo.cqrs

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*
import scala.concurrent.duration.*
import io.gatling.core.structure.ScenarioBuilder
import scala.util.Random

class IngestionLoadTest extends Simulation {

  val baseUrl = "http://localhost:8181"

  val numIngestions: Int              = Integer.getInteger("numIngestions", 100)
  val minNumCommandsPerIngestion: Int = Integer.getInteger("minNumCommandsPerIngestion", 5)
  val maxNumCommandsPerIngestion: Int = Integer.getInteger("maxNumCommandsPerIngestion", 15)
  val concurrentUsers: Int            = Integer.getInteger("concurrentUsers", 10)
  val testDuration: Int               = Integer.getInteger("testDuration", 10)

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  var ingestionIds = List[String]()

  val createIngestion: ScenarioBuilder = scenario("Create Ingestion")
    .repeat(numIngestions) {
      exec(
        http("POST /ingestion")
          .post("/ingestion")
          .check(status.is(200))
          .check(jsonPath("$.id").saveAs("ingestionId"))
      ).exec { session =>
        val ingestionId = session("ingestionId").as[String]
        ingestionIds = ingestionId :: ingestionIds
        session
      }
    }

  val setupAndProcessIngestion: ScenarioBuilder = scenario("Setup Ingestion")
    .foreach(ingestionIds, "ingestionId") {
      exec { session =>
        val numCommands = Random.between(minNumCommandsPerIngestion, maxNumCommandsPerIngestion)
        session.set("numCommandsPerIngestion", numCommands)
        val data = s"""{"SetFiles":[${(1 to numCommands).map(id => s"\"$id\"").mkString(",")}]}"""
        session.set("data", data)
        session
      }
        .exec(
          http("PUT /ingestion/{id} - Setup")
            .put("/ingestion/#{ingestionId}")
            .body(StringBody("""#data"""))
            .asJson
            .check(status.is(200))
        )
        .repeat(session => session("numCommandsPerIngestion").as[Int]) {
          exec { session =>
            val index = session("index").asOption[Int].getOrElse(0)
            val data  = s"""{"FileProcessed":${index + 1}}"""
            session.set("command", data)
            session
          }.exec(
            http("PUT /ingestion/{id} - Process")
              .put("/ingestion/#{ingestionId}")
              .body(StringBody("""#{command}"""))
              .asJson
              .check(status.is(200))
          ).exec { session =>
            val index = session("index").asOption[Int].getOrElse(0)
            session.set("index", index + 1)
            session
          }
        }
    }

  val loadIngestion: ScenarioBuilder = scenario("Load Ingestion")
    .foreach(ingestionIds, "ingestionId") {
      exec(
        http("GET /ingestion/{id}")
          .get("/ingestion/#{ingestionId}")
          .check(status.is(200))
      ).pause(1.millisecond)
    }

  val fetchAllIngestions: ScenarioBuilder = scenario("Fetch All Ingestions").exec { session =>
    session.set("offset", "")
  }
    .asLongAs(session => session("offset").as[String] != "end") {
      exec(
        http("GET /ingestion (fetch ids)")
          .get("/ingestion")
          .queryParam("offset", session => session("offset").as[String])
          // .queryParam("limit", "10")
          .check(status.is(200))
          .check(jsonPath("$.ids").findAll.saveAs("fetchedIds"))
          .check(jsonPath("$.nextOffset").optional.saveAs("nextOffset"))
      ).exec { session =>
        val nextOffset = session("nextOffset").asOption[String]
        nextOffset match {
          case Some(offset) => session.set("offset", offset)
          case None         => session.set("offset", "end")
        }
        session
      }
    }
    .foreach("#{fetchedIds}", "id") {
      exec(
        http("POST /dataload/ingestion (fetch objects)")
          .post("/dataload/ingestion")
          .body(StringBody("""["#{id}"]"""))
          .asJson
          .check(status.is(200))
      )
    }

  setUp(
    createIngestion.inject(atOnceUsers(1)),
    setupAndProcessIngestion.inject(rampUsers(concurrentUsers) during (testDuration.minutes)),
    loadIngestion.inject(constantConcurrentUsers(concurrentUsers) during (testDuration.minutes)),
    fetchAllIngestions.inject(rampUsers(concurrentUsers) during (testDuration.minutes))
  ).protocols(httpProtocol)

}
