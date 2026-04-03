package com.example.xcpro.livefollow.account

import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentApiXcAccountDataSourceTest {

    @Test
    fun exchangeGoogleIdToken_postsExchangeEndpoint_andParsesSession() = runTest {
        val requestedPath = AtomicReference<String>()
        val requestBody = AtomicReference<String>()
        val dataSource = CurrentApiXcAccountDataSource(
            httpClient = OkHttpClient.Builder().addInterceptor(
                Interceptor { chain ->
                    requestedPath.set(chain.request().url.encodedPath)
                    val buffer = Buffer()
                    chain.request().body?.writeTo(buffer)
                    requestBody.set(buffer.readUtf8())
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """
                                {
                                  "access_token": "xcpro-bearer",
                                  "token_type": "Bearer",
                                  "auth_method": "google",
                                  "user_id": "pilot-1"
                                }
                            """.trimIndent().toResponseBody(JSON_MEDIA_TYPE)
                        )
                        .build()
                }
            ).build(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = dataSource.exchangeGoogleIdToken("google-id-token")

        require(result is XcAccountRemoteResult.Success)
        assertEquals("/api/v2/auth/google/exchange", requestedPath.get())
        assertTrue(requestBody.get().orEmpty().contains("\"google_id_token\":\"google-id-token\""))
        assertEquals("xcpro-bearer", result.value.accessToken)
        assertEquals(XcAccountAuthMethod.GOOGLE, result.value.authMethod)
    }

    @Test
    fun fetchMe_usesAuthenticatedMeEndpoint_andParsesProfileAndPrivacy() = runTest {
        val requestedPath = AtomicReference<String>()
        val authorizationHeader = AtomicReference<String>()
        val dataSource = CurrentApiXcAccountDataSource(
            httpClient = OkHttpClient.Builder().addInterceptor(
                Interceptor { chain ->
                    requestedPath.set(chain.request().url.encodedPath)
                    authorizationHeader.set(chain.request().header("Authorization"))
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(mePayload().toResponseBody(JSON_MEDIA_TYPE))
                        .build()
                }
            ).build(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = dataSource.fetchMe("test-token")

        require(result is XcAccountRemoteResult.Success)
        assertEquals("/api/v2/me", requestedPath.get())
        assertEquals("Bearer test-token", authorizationHeader.get())
        assertEquals("pilot-1", result.value.profile.userId)
        assertEquals("pilot123", result.value.profile.handle)
        assertEquals("Pilot One", result.value.profile.displayName)
        assertEquals("ABC", result.value.profile.compNumber)
        assertEquals(XcFollowPolicy.APPROVAL_REQUIRED, result.value.privacy.followPolicy)
        assertEquals(XcDefaultLiveVisibility.FOLLOWERS, result.value.privacy.defaultLiveVisibility)
    }

    @Test
    fun patchProfile_propagatesStructuredConflictError() = runTest {
        val requestedPath = AtomicReference<String>()
        val dataSource = CurrentApiXcAccountDataSource(
            httpClient = OkHttpClient.Builder().addInterceptor(
                Interceptor { chain ->
                    requestedPath.set(chain.request().url.encodedPath)
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(409)
                        .message("Conflict")
                        .body(
                            """{"code":"handle_already_taken","detail":"handle already taken"}"""
                                .toResponseBody(JSON_MEDIA_TYPE)
                        )
                        .build()
                }
            ).build(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = dataSource.patchProfile(
            accessToken = "test-token",
            request = XcProfileUpdateRequest(
                handle = "pilot123",
                displayName = "Pilot One",
                compNumber = null
            )
        )

        require(result is XcAccountRemoteResult.Failure)
        assertEquals("/api/v2/me/profile", requestedPath.get())
        assertEquals("handle_already_taken", result.error.code)
        assertEquals("handle already taken", result.error.message)
        assertEquals(409, result.error.httpCode)
    }

    @Test
    fun patchPrivacy_sendsAuthenticatedPrivacyPatch() = runTest {
        val requestedPath = AtomicReference<String>()
        val authorizationHeader = AtomicReference<String>()
        val dataSource = CurrentApiXcAccountDataSource(
            httpClient = OkHttpClient.Builder().addInterceptor(
                Interceptor { chain ->
                    requestedPath.set(chain.request().url.encodedPath)
                    authorizationHeader.set(chain.request().header("Authorization"))
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(privacyPayload().toResponseBody(JSON_MEDIA_TYPE))
                        .build()
                }
            ).build(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = dataSource.patchPrivacy(
            accessToken = "privacy-token",
            request = XcPrivacyUpdateRequest(
                discoverability = XcDiscoverability.HIDDEN,
                followPolicy = XcFollowPolicy.AUTO_APPROVE,
                defaultLiveVisibility = XcDefaultLiveVisibility.PUBLIC,
                connectionListVisibility = XcConnectionListVisibility.PUBLIC
            )
        )

        require(result is XcAccountRemoteResult.Success)
        assertEquals("/api/v2/me/privacy", requestedPath.get())
        assertEquals("Bearer privacy-token", authorizationHeader.get())
        assertEquals(XcDiscoverability.HIDDEN, result.value.discoverability)
        assertEquals(XcFollowPolicy.AUTO_APPROVE, result.value.followPolicy)
        assertEquals(XcConnectionListVisibility.PUBLIC, result.value.connectionListVisibility)
        assertTrue(result.value.defaultLiveVisibility == XcDefaultLiveVisibility.PUBLIC)
    }

    @Test
    fun searchUsers_usesAuthenticatedSearchEndpoint_andParsesRelationshipState() = runTest {
        val requestedPath = AtomicReference<String>()
        val requestedQuery = AtomicReference<String?>()
        val dataSource = CurrentApiXcAccountDataSource(
            httpClient = OkHttpClient.Builder().addInterceptor(
                Interceptor { chain ->
                    requestedPath.set(chain.request().url.encodedPath)
                    requestedQuery.set(chain.request().url.queryParameter("q"))
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(searchPayload().toResponseBody(JSON_MEDIA_TYPE))
                        .build()
                }
            ).build(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = dataSource.searchUsers(
            accessToken = "search-token",
            query = "pilot"
        )

        require(result is XcAccountRemoteResult.Success)
        assertEquals("/api/v2/users/search", requestedPath.get())
        assertEquals("pilot", requestedQuery.get())
        assertEquals(1, result.value.size)
        assertEquals("pilot.two", result.value.first().handle)
        assertEquals(XcRelationshipState.OUTGOING_PENDING, result.value.first().relationshipState)
    }

    @Test
    fun createFollowRequest_postsTargetUserId_andParsesCounterpart() = runTest {
        val requestedPath = AtomicReference<String>()
        val requestBody = AtomicReference<String>()
        val dataSource = CurrentApiXcAccountDataSource(
            httpClient = OkHttpClient.Builder().addInterceptor(
                Interceptor { chain ->
                    requestedPath.set(chain.request().url.encodedPath)
                    val buffer = Buffer()
                    chain.request().body?.writeTo(buffer)
                    requestBody.set(buffer.readUtf8())
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(followRequestPayload().toResponseBody(JSON_MEDIA_TYPE))
                        .build()
                }
            ).build(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = dataSource.createFollowRequest(
            accessToken = "follow-token",
            targetUserId = "pilot-2"
        )

        require(result is XcAccountRemoteResult.Success)
        assertEquals("/api/v2/follow-requests", requestedPath.get())
        assertTrue(requestBody.get().orEmpty().contains("\"target_user_id\":\"pilot-2\""))
        assertEquals("request-1", result.value.requestId)
        assertEquals(XcFollowRequestDirection.OUTGOING, result.value.direction)
        assertEquals("pilot.two", result.value.counterpart.handle)
        assertEquals(XcRelationshipState.OUTGOING_PENDING, result.value.relationshipState)
    }

    @Test
    fun fetchMe_ioFailure_usesFriendlyAccountMessage() = runTest {
        val dataSource = CurrentApiXcAccountDataSource(
            httpClient = OkHttpClient.Builder().addInterceptor(
                Interceptor { throw UnknownHostException("api.xcpro.com.au") }
            ).build(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val result = dataSource.fetchMe("test-token")

        require(result is XcAccountRemoteResult.Failure)
        assertEquals("XC account network error. Check connection and retry.", result.error.message)
        assertFalse(result.error.message.contains("hostname", ignoreCase = true))
    }

    private fun mePayload(): String {
        return """
            {
              "user_id": "pilot-1",
              "handle": "pilot123",
              "display_name": "Pilot One",
              "comp_number": "ABC",
              "privacy": {
                "discoverability": "searchable",
                "follow_policy": "approval_required",
                "default_live_visibility": "followers",
                "connection_list_visibility": "owner_only"
              }
            }
        """.trimIndent()
    }

    private fun privacyPayload(): String {
        return """
            {
              "discoverability": "hidden",
              "follow_policy": "auto_approve",
              "default_live_visibility": "public",
              "connection_list_visibility": "public"
            }
        """.trimIndent()
    }

    private fun searchPayload(): String {
        return """
            {
              "users": [
                {
                  "user_id": "pilot-2",
                  "handle": "pilot.two",
                  "display_name": "Pilot Two",
                  "comp_number": "TWO",
                  "relationship_state": "outgoing_pending"
                }
              ]
            }
        """.trimIndent()
    }

    private fun followRequestPayload(): String {
        return """
            {
              "request_id": "request-1",
              "status": "pending",
              "direction": "outgoing",
              "counterpart": {
                "user_id": "pilot-2",
                "handle": "pilot.two",
                "display_name": "Pilot Two",
                "comp_number": "TWO"
              },
              "relationship_state": "outgoing_pending"
            }
        """.trimIndent()
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
