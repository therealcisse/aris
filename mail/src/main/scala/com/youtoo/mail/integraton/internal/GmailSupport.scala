package com.youtoo
package mail
package integration
package internal

import zio.*

import com.youtoo.mail.model.*

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import java.util.Collections
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential

object GmailSupport {

  private val SCOPES = Collections.singletonList(GmailScopes.GMAIL_LABELS)

  def authenticate(config: AuthConfig): Task[Gmail] = ZIO.attempt {

    val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    val jsonFactory = GsonFactory.getDefaultInstance

    val credential = new GoogleCredential.Builder()
      .setTransport(httpTransport)
      .setJsonFactory(jsonFactory)
      .setClientSecrets(config.clientId.value, config.clientSecret.value)
      .setServiceAccountScopes(SCOPES)
      .build()

    credential.refreshToken()

    new Gmail.Builder(httpTransport, jsonFactory, credential)
      .setApplicationName("YouToo App")
      .build()
  }

}

