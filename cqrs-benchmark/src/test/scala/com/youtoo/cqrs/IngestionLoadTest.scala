package com.youtoo.cqrs

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*
import scala.concurrent.duration.*
import io.gatling.core.structure.ScenarioBuilder
import scala.util.Random
import zio.schema.codec.*
import com.youtoo.cqrs.Codecs.json.given
import com.youtoo.cqrs.example.model.*
import java.nio.charset.StandardCharsets
import com.youtoo.cqrs.example.BenchmarkServer

class IngestionLoadTest extends Simulation {

  val baseUrl = "http://localhost:8181"

  val numIngestions: Int = Integer.getInteger("numIngestions", 100)
  val minNumCommandsPerIngestion: Int = Integer.getInteger("minNumCommandsPerIngestion", 5)
  val maxNumCommandsPerIngestion: Int = Integer.getInteger("maxNumCommandsPerIngestion", 15)
  val concurrentUsers: Int = Integer.getInteger("concurrentUsers", 10)
  val testDuration: Int = Integer.getInteger("testDuration", 10)

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
          .check(jsonPath("$.id").saveAs("ingestionId")),
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
        val data = summon[BinaryCodec[IngestionCommand]].encode(
          IngestionCommand.SetFiles(Set((1 to numCommands).map(_.toString).toSeq*)),
        )
        session.setAll(
          "data" -> String(data.toArray, StandardCharsets.UTF_8.name),
          "numCommandsPerIngestion" -> numCommands,
        )
      }
        .exec(
          http("PUT /ingestion/{id} - Setup")
            .put("/ingestion/#{ingestionId}")
            .body(StringBody("""#{data}"""))
            .check(status.is(200)),
        )
        .repeat(session => session("numCommandsPerIngestion").as[Int], "index") {
          exec { session =>
            val index = session("index").as[Int]
            val data = summon[BinaryCodec[IngestionCommand]].encode(IngestionCommand.FileProcessed(s"${index + 1}"))
            session.set("command", String(data.toArray, StandardCharsets.UTF_8.name))
          }.exec(
            http("PUT /ingestion/{id} - Process")
              .put("/ingestion/#{ingestionId}")
              .body(StringBody("""#{command}"""))
              .check(status.is(200)),
          )
        }
    }

  val loadIngestion: ScenarioBuilder = scenario("Load Ingestion")
    .foreach(ingestionIds, "ingestionId") {
      exec(
        http("GET /ingestion/{id}")
          .get("/ingestion/#{ingestionId}")
          .check(status.is(200)),
      ).pause(1.millisecond)
    }

  var allIds = Vector[String]()

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
          .check(
            jsonPath("$.ids").find
              .transform(_.drop(1).dropRight(1).split(",").toVector.sortWith(_ < _))
              .saveAs("fetchedIds"),
          ),
      ).exec { session =>
        val fetchedIds = session("fetchedIds").asOption[Vector[String]].getOrElse(Vector())
        allIds = allIds ++ fetchedIds

        if fetchedIds.size < BenchmarkServer.Limit then session.set("offset", "end")
        else
          allIds.minOption match {
            case None => session.set("offset", "end")
            case Some(offset) => session.set("offset", offset)
          }
      }
    }
    .foreach(allIds.grouped(3).toSeq, "ids") {
      exec { session =>
        session.set("data", session("ids").as[Vector[String]].map(id => s""""$id"""").mkString("[", ",", "]"))
      }.exec(
        http("POST /dataload/ingestion (fetch objects)")
          .post("/dataload/ingestion")
          .body(StringBody("""#{data}"""))
          .check(status.is(200)),
      )
    }

  setUp(
    createIngestion.inject(atOnceUsers(1)),
    setupAndProcessIngestion.inject(rampUsers(concurrentUsers) during (testDuration.minutes)),
    loadIngestion.inject(constantConcurrentUsers(concurrentUsers) during (testDuration.minutes)),
    fetchAllIngestions.inject(rampUsers(concurrentUsers) during (testDuration.minutes)),
  ).protocols(httpProtocol)

}
