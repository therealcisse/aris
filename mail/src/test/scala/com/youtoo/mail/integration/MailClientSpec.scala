package com.youtoo
package mail
package integration

import com.youtoo.mail.model.*

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*

import org.mockito.Mockito
import org.mockito.ArgumentMatchers.any

import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.{Label, ListLabelsResponse, ListMessagesResponse, Message}

import zio.telemetry.opentelemetry.tracing.*
import com.youtoo.postgres.*

object MailClientTest extends ZIOSpecDefault, TestSupport {

  def labels_mockGmail(
    listLabelsResponse: ListLabelsResponse = new ListLabelsResponse(),
  ): Gmail = {
    val gmailMock = Mockito.mock(classOf[Gmail])

    val usersMock = Mockito.mock(classOf[gmailMock.Users])
    val labelsMock = Mockito.mock(classOf[usersMock.Labels])
    val labelsListMock = Mockito.mock(classOf[labelsMock.List])

    Mockito.when(gmailMock.users()).thenReturn(usersMock)
    Mockito.when(usersMock.labels()).thenReturn(labelsMock)
    Mockito.when(labelsMock.list(any())).thenReturn(labelsListMock)
    Mockito.when(labelsListMock.execute()).thenReturn(listLabelsResponse)

    gmailMock
  }

  def messages_mockGmail(
    listMessagesResponse: ListMessagesResponse = new ListMessagesResponse(),
  ): Gmail = {
    val gmailMock = Mockito.mock(classOf[Gmail])
    val usersMock = Mockito.mock(classOf[gmailMock.Users])
    val messagesMock = Mockito.mock(classOf[usersMock.Messages])
    val messagesListMock = Mockito.mock(classOf[messagesMock.List])

    Mockito.when(gmailMock.users()).thenReturn(usersMock)

    Mockito.when(usersMock.messages()).thenReturn(messagesMock)
    Mockito.when(messagesMock.list(any())).thenReturn(messagesListMock)
    Mockito.when(messagesListMock.setMaxResults(any())).thenReturn(messagesListMock)
    Mockito.when(messagesListMock.setLabelIds(any())).thenReturn(messagesListMock)
    Mockito.when(messagesListMock.setPageToken(any())).thenReturn(messagesListMock)
    Mockito.when(messagesListMock.execute()).thenReturn(listMessagesResponse)

    gmailMock
  }

  def message_mockGmail(
    messageResponse: Message = new Message(),
  ): Gmail = {
    val gmailMock = Mockito.mock(classOf[Gmail])
    val usersMock = Mockito.mock(classOf[gmailMock.Users])
    val messagesMock = Mockito.mock(classOf[usersMock.Messages])
    val messagesGetMock = Mockito.mock(classOf[messagesMock.Get])

    Mockito.when(gmailMock.users()).thenReturn(usersMock)

    Mockito.when(usersMock.messages()).thenReturn(messagesMock)

    Mockito.when(messagesMock.get(any(), any())).thenReturn(messagesGetMock)
    Mockito.when(messagesGetMock.setFormat(any())).thenReturn(messagesGetMock)
    Mockito.when(messagesGetMock.execute()).thenReturn(messageResponse)

    gmailMock
  }

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("MailClient")(
      test("loadLabels") {
        val label = new Label().setId("label-1").setName("label-name-1").setMessagesTotal(10)
        val listResponse = new ListLabelsResponse().setLabels(java.util.Arrays.asList(label))
        val gmailMock = labels_mockGmail(listLabelsResponse = listResponse)

        val accountId = MailAccount.Id(1)

        val mockEnv = GmailPoolMock.Get(
          equalTo(accountId),
          value(gmailMock),
        )

        (for {
          client <- ZIO.service[MailClient]
          labels <- client.loadLabels(accountId)
        } yield assert(labels)(hasSize(equalTo(1))))
          .provideSomeLayer[Scope & Tracing](
            mockEnv.toLayer >>> MailClient.live(),
          )
      },
      test("fetchMails") {
        val message = new Message().setId("message-1")
        val listResponse =
          new ListMessagesResponse().setMessages(java.util.Arrays.asList(message)).setNextPageToken("token-1")
        val gmailMock = messages_mockGmail(listMessagesResponse = listResponse)

        val accountId = MailAccount.Id(1)
        val labelKey = MailLabels.LabelKey("label-1")
        val address = MailAddress(accountId, labelKey)

        val mockEnv = GmailPoolMock.Get(
          equalTo(accountId),
          value(gmailMock),
        )

        (for {
          client <- ZIO.service[MailClient]
          result <- client.fetchMails(address, None)
        } yield assert(result)(isSome(anything)) && assert(result.get._1)(hasSize(equalTo(1))) && assert(result.get._2)(
          equalTo(MailToken("token-1")),
        ))
          .provideSomeLayer[Scope & Tracing](
            mockEnv.toLayer >>> MailClient.live(),
          )
      },
      test("loadMessage") {
        val message = new Message().setId("message-1").setRaw("raw-body").setInternalDate(1000L)
        val gmailMock = message_mockGmail(messageResponse = message)

        val accountId = MailAccount.Id(1)
        val messageId = MailData.Id("message-1")

        val mockEnv = GmailPoolMock.Get(
          equalTo(accountId),
          value(gmailMock),
        )

        (for {
          client <- ZIO.service[MailClient]
          result <- client.loadMessage(accountId, messageId)
        } yield assert(result)(
          isSome(
            hasField("id", (m: MailData) => m.id, equalTo(messageId)) &&
              hasField("body", (m: MailData) => m.body, equalTo(MailData.Body("raw-body"))) &&
              hasField("internalDate", (m: MailData) => m.internalDate, equalTo(InternalDate(Timestamp(1000L)))),
          ),
        ))
          .provideSomeLayer[Scope & Tracing](
            mockEnv.toLayer >>> MailClient.live(),
          )
      },
    ).provideSomeLayerShared(
      ZLayer.make[Tracing](
        tracingMockLayer(),
        zio.telemetry.opentelemetry.OpenTelemetry.contextZIO,
      ),
    )
}
