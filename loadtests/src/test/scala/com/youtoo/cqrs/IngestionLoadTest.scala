package com.youtoo
package cqrs

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*
import scala.concurrent.duration.*
import io.gatling.core.structure.ScenarioBuilder
import zio.schema.codec.*
import com.youtoo.cqrs.Codecs.json.given
import com.youtoo.ingestion.model.*
import java.nio.charset.StandardCharsets
import com.youtoo.ingestion.IngestionBenchmarkServer

import zio.prelude.*

class IngestionLoadTest extends Simulation {
  val port: Int = Integer.getInteger("port", 8181)

  val baseUrl = s"http://localhost:$port"

  val numCommandsPerIngestion: Int = Integer.getInteger("numCommandsPerIngestion", 10)
  val concurrentUsers: Int = Integer.getInteger("concurrentUsers", 1)
  val testDuration: Int = Integer.getInteger("testDuration", 60)

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  var allIds = Set[String]()

  val process: ScenarioBuilder = scenario("Process Ingestion")
    .exec(
      http("POST /ingestion")
        .post("/ingestion")
        .check(status.is(200))
        .check(jsonPath("$.id").saveAs("ingestionId")),
    )
    .exec { session =>
      val numCommands =
        numCommandsPerIngestion
      val data = summon[BinaryCodec[IngestionCommand]].encode(
        IngestionCommand.SetFiles(
          NonEmptySet(IngestionFile.Id(1L), (2L to numCommands).map(n => IngestionFile.Id(n)).toSeq*),
        ),
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
    .exec(
      http("GET /ingestion/{id}/validate - Verify state resolved")
        .get("/ingestion/#{ingestionId}/validate")
        .queryParam("numFiles", session => session("numCommandsPerIngestion").as[Int])
        .queryParam("status", _ => "resolved")
        .check(status.is(200)),
    )
    .repeat(session => session("numCommandsPerIngestion").as[Int], "index") {
      exec { session =>
        val index = session("index").as[Int]
        val data = summon[BinaryCodec[IngestionCommand]].encode(
          IngestionCommand.FileProcessing(IngestionFile.Id(index + 1L)),
        )
        session.set("command", String(data.toArray, StandardCharsets.UTF_8.name))
      }.exec(
        http("PUT /ingestion/{id} - Processing")
          .put("/ingestion/#{ingestionId}")
          .body(StringBody("""#{command}"""))
          .check(status.is(200)),
      ).exec(
        http("GET /ingestion/{id}")
          .get("/ingestion/#{ingestionId}")
          .check(jsonPath("$.id").saveAs("ingestionId"))
          .check(status.is(200)),
      )
    }
    .exec(
      http("GET /ingestion/{id}/validate - Verify state processing")
        .get("/ingestion/#{ingestionId}/validate")
        .queryParam("numFiles", session => session("numCommandsPerIngestion").as[Int])
        .queryParam("status", _ => "processing")
        .check(status.is(200)),
    )
    .repeat(session => session("numCommandsPerIngestion").as[Int], "index") {
      exec { session =>
        val index = session("index").as[Int]
        val data = summon[BinaryCodec[IngestionCommand]].encode(
          IngestionCommand.FileProcessed(IngestionFile.Id(index + 1L)),
        )
        session.set("command", String(data.toArray, StandardCharsets.UTF_8.name))
      }.exec(
        http("PUT /ingestion/{id} - Processed")
          .put("/ingestion/#{ingestionId}")
          .body(StringBody("""#{command}"""))
          .check(status.is(200)),
      ).exec(
        http("GET /ingestion/{id}")
          .get("/ingestion/#{ingestionId}")
          .check(jsonPath("$.id").saveAs("ingestionId"))
          .check(status.is(200)),
      )
    }
    .exec(
      http("GET /ingestion/{id}/validate - Verify state processed")
        .get("/ingestion/#{ingestionId}/validate")
        .queryParam("numFiles", session => session("numCommandsPerIngestion").as[Int])
        .queryParam("status", _ => "completed")
        .check(status.is(200)),
    )

  val fetch: ScenarioBuilder = scenario("Load Ingestions").exec { session =>
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

        if fetchedIds.size < IngestionBenchmarkServer.FetchSize then session.set("offset", "end")
        else
          allIds.minOption match {
            case None => session.set("offset", "end")
            case Some(offset) => session.set("offset", offset)
          }
      }
    }
    .exec { session =>
      session.set("allIds", allIds.grouped(8).toSeq)
    }
    .foreach("#{allIds}", "ids") {
      exec { session =>
        val data = session("ids").as[Set[String]]
        session.set(
          "data",
          String(summon[BinaryCodec[Set[String]]].encode(data).toArray, StandardCharsets.UTF_8.name),
        )
      }.exec(
        http("POST /dataload/ingestion (fetch objects)")
          .post("/dataload/ingestion")
          .body(StringBody("""#{data}"""))
          .check(status.is(200)),
      )
    }

  setUp(
    process.inject(constantUsersPerSec(concurrentUsers) during (testDuration.seconds)),
    fetch.inject(constantUsersPerSec(concurrentUsers) during (testDuration.seconds)),
  ).protocols(httpProtocol)

}
