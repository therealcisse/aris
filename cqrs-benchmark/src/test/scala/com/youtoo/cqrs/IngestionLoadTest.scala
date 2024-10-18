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

import zio.prelude.*

class IngestionLoadTest extends Simulation {
  val port: Int = Integer.getInteger("port", 8181)

  val baseUrl = s"http://localhost:$port"

  val numIngestions: Int = Integer.getInteger("numIngestions", 100)
  val minNumCommandsPerIngestion: Int = Integer.getInteger("minNumCommandsPerIngestion", 10)
  val maxNumCommandsPerIngestion: Int = Integer.getInteger("maxNumCommandsPerIngestion", 100)
  val concurrentUsers: Int = Integer.getInteger("concurrentUsers", 4)
  val testDuration: Int = Integer.getInteger("testDuration", 5)

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  var ingestionIds = List[String]()
  var allIds = Vector[String]()

  val scn: ScenarioBuilder = scenario("Process Ingestion")
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
    .exec { session =>
      session.set("ingestionIds", ingestionIds)
    }
    .foreach("#{ingestionIds}", "ingestionId") {
      exec { session =>
        val numCommands = Random.between(minNumCommandsPerIngestion, maxNumCommandsPerIngestion)
        val data = summon[BinaryCodec[IngestionCommand]].encode(
          IngestionCommand.SetFiles(NonEmptySet("1", (2 to numCommands).map(_.toString).toSeq*)),
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
          ).exec(
            http("GET /ingestion/{id}")
              .get("/ingestion/#{ingestionId}")
              .check(status.is(200)),
          )
        }
        .exec(
          http("GET /ingestion/{id}/validate - Verify state")
            .get("/ingestion/#{ingestionId}/validate")
            .queryParam("numFiles", session => session("numCommandsPerIngestion").as[Int])
            .check(status.is(200)),
        )
    }
    .exec { session =>
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

        if fetchedIds.size < BenchmarkServer.FetchSize then session.set("offset", "end")
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
        val data = session("ids").as[Vector[String]]
        session.set(
          "data",
          String(summon[BinaryCodec[Vector[String]]].encode(data).toArray, StandardCharsets.UTF_8.name),
        )
      }.exec(
        http("POST /dataload/ingestion (fetch objects)")
          .post("/dataload/ingestion")
          .body(StringBody("""#{data}"""))
          .check(status.is(200)),
      )
    }

  setUp(
    scn.inject(rampUsers(concurrentUsers) during testDuration.minutes),
  ).protocols(httpProtocol)

}
