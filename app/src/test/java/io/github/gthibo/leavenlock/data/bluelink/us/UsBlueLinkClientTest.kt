package io.github.gthibo.leavenlock.data.bluelink.us

import io.github.gthibo.leavenlock.data.bluelink.model.CommandResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * US lock/unlock against a fake Hyundai server (Ktor MockEngine). No network, no real car.
 * Request shape follows hyundai_kia_connect_api `lock_action` / `check_action_status` and bluelinky.
 */
class UsBlueLinkClientTest {

    private val vin = "KMHTESTVIN0000001"
    private val regId = "REG123"

    private val tokenJson = """{"access_token":"tok-1","expires_in":"1799"}"""
    private val vehiclesJson = """
        {"enrolledVehicleDetails":[{"vehicleDetails":{
          "regid":"$regId","vin":"$vin","vehicleGeneration":"3",
          "nickName":"Ioniq","modelCode":"IONIQ 5","enrollmentStatus":"ACTIVE"}}]}
    """.trimIndent()

    private class Fake(
        val commandResponse: MockRequestHandleScope.() -> HttpResponseData,
        val pollStatuses: MutableList<String?>,
    ) {
        val requests = mutableListOf<HttpRequestData>()
        val commandRequests get() = requests.filter { "/rcs/rdo/" in it.url.encodedPath }
        val pollRequests get() = requests.filter { it.url.encodedPath.endsWith("/rmt/getRunningStatus") }
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf("Content-Type", "application/json"))

    private fun client(
        pin: String? = "1234",
        pollStatuses: List<String?> = listOf("SUCCESS"),
        commandResponse: MockRequestHandleScope.() -> HttpResponseData = {
            respond("", HttpStatusCode.OK, headersOf("tmsTid", "TID-9"))
        },
        store: UsCredentialStore = UsCredentialStore.None,
        tokenResponse: MockRequestHandleScope.() -> HttpResponseData = { json(tokenJson) },
        statusJson: String = "{}",
        diag: (String) -> Unit = {},
    ): Pair<UsBlueLinkClient, Fake> {
        val fake = Fake(commandResponse, pollStatuses.toMutableList())
        val engine = MockEngine { request ->
            fake.requests += request
            val path = request.url.encodedPath
            when {
                path.endsWith("/v2/ac/oauth/token") -> tokenResponse(this)
                path.contains("/ac/v2/enrollment/details/") -> json(vehiclesJson)
                path.contains("/ac/v2/rcs/rdo/") -> fake.commandResponse(this)
                path.endsWith("/ac/v2/rcs/rvs/vehicleStatus") -> json(statusJson)
                path.endsWith("/ac/v2/rmt/getRunningStatus") -> {
                    val next = if (fake.pollStatuses.isEmpty()) null else fake.pollStatuses.removeAt(0)
                    if (next == null) json("""{"status":"PENDING"}""") else json("""{"status":"$next"}""")
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        return UsBlueLinkClient(HttpClient(engine), pinProvider = { pin }, credentialStore = store, diag = diag) to fake
    }

    private suspend fun UsBlueLinkClient.signIn() {
        assertTrue(loginWithPassword("me@example.com", "pw") is CommandResult.Success)
    }

    private fun HttpRequestData.bodyJson() =
        Json.parseToJsonElement((body as TextContent).text).jsonObject

    @Test
    fun lockPostsRdoOffWithVehicleHeadersPinAndBody() = runTest {
        val (c, fake) = client()
        c.signIn()

        val result = c.lock(regId)

        assertEquals(CommandResult.Success("Locked"), result)
        val cmd = fake.commandRequests.single()
        assertEquals(HttpMethod.Post, cmd.method)
        assertTrue(cmd.url.encodedPath.endsWith("/ac/v2/rcs/rdo/off"))
        assertEquals(regId, cmd.headers["registrationId"])
        assertEquals("3", cmd.headers["gen"])
        assertEquals(vin, cmd.headers["vin"])
        assertEquals(vin, cmd.headers["APPCLOUD-VIN"])
        assertEquals("1234", cmd.headers["blueLinkServicePin"])
        assertEquals("tok-1", cmd.headers["accessToken"])
        assertEquals("me@example.com", cmd.headers["username"])
        val body = cmd.bodyJson()
        assertEquals("me@example.com", body["userName"]!!.jsonPrimitive.content)
        assertEquals(vin, body["vin"]!!.jsonPrimitive.content)
    }

    @Test
    fun unlockPostsRdoOn() = runTest {
        val (c, fake) = client()
        c.signIn()

        assertEquals(CommandResult.Success("Unlocked"), c.unlock(regId))
        assertTrue(fake.commandRequests.single().url.encodedPath.endsWith("/ac/v2/rcs/rdo/on"))
    }

    @Test
    fun pollUsesTransactionIdAndRemotePollHeaders() = runTest {
        val (c, fake) = client(pollStatuses = listOf(null, null, "SUCCESS"))
        c.signIn()

        assertEquals(CommandResult.Success("Locked"), c.lock(regId))
        assertEquals(3, fake.pollRequests.size)
        val poll = fake.pollRequests.first()
        assertEquals("TID-9", poll.headers["tid"])
        assertEquals("me@example.com", poll.headers["login_id"])
        assertEquals("REMOTE_POLL", poll.headers["service_type"])
        assertEquals(regId, poll.headers["registrationId"])
    }

    @Test
    fun transactionIdIsReadFromAlternateHeaders() = runTest {
        for (name in listOf("transactionId", "Xid")) {
            val (c, fake) = client(commandResponse = { respond("", HttpStatusCode.OK, headersOf(name, "T-$name")) })
            c.signIn()
            assertEquals(CommandResult.Success("Locked"), c.lock(regId))
            assertEquals("T-$name", fake.pollRequests.single().headers["tid"])
        }
    }

    @Test
    fun carReportedErrorIsFailure() = runTest {
        val (c, _) = client(pollStatuses = listOf("ERROR"))
        c.signIn()

        val result = c.lock(regId)
        assertTrue(result is CommandResult.Failure)
        assertTrue((result as CommandResult.Failure).reason.startsWith("Car reported the lock failed"))
    }

    @Test
    fun noConfirmationWithinWindowIsFailureAfterTenPolls() = runTest {
        val (c, fake) = client(pollStatuses = emptyList())
        c.signIn()

        val result = c.lock(regId)
        assertEquals(
            CommandResult.Failure("US lock sent but the car didn't confirm within 30s. It may still go through."),
            result,
        )
        assertEquals(10, fake.pollRequests.size)
        assertEquals(1, fake.commandRequests.size) // never re-sends on its own
    }

    @Test
    fun missingTransactionIdReturnsSentWithoutPolling() = runTest {
        val (c, fake) = client(commandResponse = { respond("", HttpStatusCode.OK) })
        c.signIn()

        assertEquals(CommandResult.Success("Lock sent"), c.lock(regId))
        assertTrue(fake.pollRequests.isEmpty())
    }

    @Test
    fun errorCodeInHttp200BodyIsFailureWithHyundaiMessage() = runTest {
        val (c, fake) = client(commandResponse = {
            json("""{"errorCode":502,"errorMessage":"HATA remoteVehicleStatus service failed"}""")
        })
        c.signIn()

        assertEquals(
            CommandResult.Failure("Hyundai error 502: HATA remoteVehicleStatus service failed"),
            c.lock(regId),
        )
        assertTrue(fake.pollRequests.isEmpty())
    }

    @Test
    fun http429IsRateLimited() = runTest {
        val (c, _) = client(commandResponse = { respond("", HttpStatusCode.TooManyRequests) })
        c.signIn()
        assertEquals(CommandResult.RateLimited, c.lock(regId))
    }

    @Test
    fun http401IsRetriableFailureAndForcesFreshSignInNextTime() = runTest {
        val (c, fake) = client(commandResponse = { respond("", HttpStatusCode.Unauthorized) })
        c.signIn()
        val tokenCallsBefore = fake.requests.count { it.url.encodedPath.endsWith("/oauth/token") }

        val result = c.lock(regId)
        assertEquals(CommandResult.Failure("Hyundai rejected the session (HTTP 401) for lock."), result)

        c.lock(regId)
        val tokenCallsAfter = fake.requests.count { it.url.encodedPath.endsWith("/oauth/token") }
        assertEquals(tokenCallsBefore + 1, tokenCallsAfter)
    }

    @Test
    fun otherHttpErrorIsFailure() = runTest {
        val (c, _) = client(commandResponse = { respond("", HttpStatusCode.ServiceUnavailable) })
        c.signIn()
        assertEquals(CommandResult.Failure("Hyundai returned HTTP 503 for lock."), c.lock(regId))
    }

    @Test
    fun missingPinFailsWithoutSendingAnything() = runTest {
        for (pin in listOf(null, "", "  ")) {
            val (c, fake) = client(pin = pin)
            c.signIn()
            val result = c.lock(regId)
            assertEquals(
                CommandResult.Failure("BlueLink PIN needed for US lock — sign in again and enter your 4-digit PIN."),
                result,
            )
            assertTrue(fake.commandRequests.isEmpty())
        }
    }

    @Test
    fun notSignedInReturnsNotAuthenticatedWithoutAnyRequest() = runTest {
        val (c, fake) = client()
        assertEquals(CommandResult.NotAuthenticated, c.lock(regId))
        assertEquals(CommandResult.NotAuthenticated, c.unlock(regId))
        assertTrue(fake.requests.isEmpty())
    }

    @Test
    fun unknownVehicleFailsWithoutSendingCommand() = runTest {
        val (c, fake) = client()
        c.signIn()
        assertEquals(CommandResult.Failure("Selected vehicle not found on US account"), c.lock("OTHER"))
        assertTrue(fake.commandRequests.isEmpty())
    }

    @Test
    fun pinIsTrimmed() = runTest {
        val (c, fake) = client(pin = " 4321 ")
        c.signIn()
        c.lock(regId)
        assertEquals("4321", fake.commandRequests.single().headers["blueLinkServicePin"])
    }

    // ---- Saved sign-in (credentials survive settings changes, app kills and reboots) ----

    private class FakeStore(var saved: UsCredentials? = null) : UsCredentialStore {
        var clears = 0
        override fun load() = saved
        override fun save(credentials: UsCredentials) { saved = credentials }
        override fun clear() { saved = null; clears++ }
    }

    private val tokenRequests: Fake.() -> List<HttpRequestData> =
        { requests.filter { it.url.encodedPath.endsWith("/v2/ac/oauth/token") } }

    @Test
    fun successfulSignInSavesTrimmedEmailAndPassword() = runTest {
        val store = FakeStore()
        val (c, _) = client(store = store)

        assertTrue(c.loginWithPassword("  me@example.com ", "pw") is CommandResult.Success)

        assertEquals(UsCredentials("me@example.com", "pw"), store.saved)
    }

    @Test
    fun failedSignInSavesNothingAndForgetsOldCredentials() = runTest {
        val store = FakeStore(UsCredentials("old@example.com", "old"))
        val (c, _) = client(store = store, tokenResponse = { respond("", HttpStatusCode.Unauthorized) })

        assertTrue(c.loginWithPassword("me@example.com", "wrong") is CommandResult.Failure)

        assertNull(store.saved)
        assertFalse(c.isAuthenticated())
    }

    @Test
    fun freshClientSignsInWithSavedCredentialsAndLocks() = runTest {
        // A new client = what the app builds after a settings change, app kill or reboot.
        val store = FakeStore(UsCredentials("me@example.com", "pw"))
        val (c, fake) = client(store = store)

        assertTrue(c.isAuthenticated())
        assertEquals(CommandResult.Success("Locked"), c.lock(regId))

        val login = fake.tokenRequests().single().bodyJson()
        assertEquals("me@example.com", login["username"]!!.jsonPrimitive.content)
        assertEquals("pw", login["password"]!!.jsonPrimitive.content)
        assertEquals("me@example.com", fake.commandRequests.single().headers["username"])
    }

    @Test
    fun isAuthenticatedAloneSendsNoRequest() = runTest {
        val (c, fake) = client(store = FakeStore(UsCredentials("me@example.com", "pw")))
        assertTrue(c.isAuthenticated())
        assertTrue(fake.requests.isEmpty())
    }

    @Test
    fun noSavedCredentialsMeansNotSignedInAndNoRequest() = runTest {
        for (saved in listOf(null, UsCredentials("me@example.com", ""), UsCredentials("", "pw"))) {
            val (c, fake) = client(store = FakeStore(saved))
            assertFalse(c.isAuthenticated())
            assertFalse(c.ensureFreshSession())
            assertEquals(CommandResult.NotAuthenticated, c.lock(regId))
            assertTrue(fake.requests.isEmpty())
        }
    }

    @Test
    fun rejectedReSignInKeepsSavedCredentialsForNextTry() = runTest {
        // A network blip or Hyundai outage must not erase the saved sign-in.
        val store = FakeStore(UsCredentials("me@example.com", "pw"))
        val (c, fake) = client(store = store, tokenResponse = { respond("", HttpStatusCode.ServiceUnavailable) })

        assertEquals(CommandResult.NotAuthenticated, c.lock(regId))

        assertEquals(UsCredentials("me@example.com", "pw"), store.saved)
        assertTrue(fake.commandRequests.isEmpty())
    }

    @Test
    fun signOutErasesSavedCredentials() = runTest {
        val store = FakeStore()
        val (c, fake) = client(store = store)
        c.signIn()

        c.clearSession()

        assertNull(store.saved)
        assertFalse(c.isAuthenticated())
        val before = fake.requests.size
        assertEquals(CommandResult.NotAuthenticated, c.lock(regId))
        assertEquals(before, fake.requests.size)
    }

    @Test
    fun validTokenIsReusedWithoutSigningInAgain() = runTest {
        val (c, fake) = client(store = FakeStore(UsCredentials("me@example.com", "pw")))
        c.lock(regId)
        c.lock(regId)
        assertEquals(1, fake.tokenRequests().size)
    }

    @Test
    fun credentialsNeverAppearInToString() {
        val text = UsCredentials("me@example.com", "secret-pw").toString()
        assertFalse(text.contains("secret-pw"))
        assertFalse(text.contains("me@example.com"))
    }

    // ---- Report time parsing ----

    private fun statusWith(dateTimeField: String) =
        """{"vehicleStatus":{"doorLock":true,"engine":false$dateTimeField,"lastStatusDate":"20261103140500"}}"""

    private suspend fun statusTimestamp(dateTimeField: String): Long {
        val (c, _) = client(statusJson = statusWith(dateTimeField))
        c.signIn()
        return c.status(regId, forceRefresh = false).timestamp
    }

    @Test
    fun reportTimeWithZoneParses() = runTest {
        val ts = statusTimestamp(""","dateTime":"2026-11-03T14:05:00Z"""")
        assertEquals(java.time.Instant.parse("2026-11-03T14:05:00Z").toEpochMilli(), ts)
    }

    @Test
    fun unparseableReportTimeIsUnknown() = runTest {
        // No zone -> unknown time.
        assertEquals(0L, statusTimestamp(""","dateTime":"2026-11-03T14:05:00""""))
    }

    @Test
    fun missingReportTimeIsUnknown() = runTest {
        assertEquals(0L, statusTimestamp(""))
    }

    @Test
    fun statusReadWritesNothingToTheActivityLog() = runTest {
        val log = mutableListOf<String>()
        val (c, _) = client(statusJson = statusWith(""","dateTime":"2026-11-03T14:05:00Z""""), diag = { log += it })
        c.signIn()
        c.status(regId, forceRefresh = false)
        assertTrue(log.toString(), log.isEmpty())
    }

    @Test
    fun carReportTimeIsSetOnlyWhenHyundaiGivesOne() = runTest {
        val (c1, _) = client(statusJson = statusWith(""","dateTime":"2026-11-03T17:21:09Z""""))
        c1.signIn()
        assertEquals(
            java.time.Instant.parse("2026-11-03T17:21:09Z").toEpochMilli(),
            c1.status(regId, forceRefresh = false).reportedAtEpochMs,
        )
        val (c2, _) = client(statusJson = statusWith(""))
        c2.signIn()
        assertNull(c2.status(regId, forceRefresh = false).reportedAtEpochMs)
    }

    // ---- Range ----

    private suspend fun rangeOf(extra: String): Int? {
        val (c, _) = client(statusJson = """{"vehicleStatus":{"doorLock":true,"engine":false$extra}}""")
        c.signIn()
        return c.status(regId, forceRefresh = false).rangeMiles
    }

    private fun evRange(value: String, unit: Int?): String {
        val unitField = if (unit == null) "" else ""","unit":$unit"""
        return ""","evStatus":{"batteryStatus":80,"drvDistance":[{"rangeByFuel":""" +
            """{"totalAvailableRange":{"value":$value$unitField}}}]}"""
    }

    @Test
    fun evRangeInMilesIsUsedAsIs() = runTest {
        assertEquals(241, rangeOf(evRange("241", unit = 3)))
        assertEquals(241, rangeOf(evRange("241.0", unit = 2)))
    }

    @Test
    fun evRangeInKmIsConvertedToMiles() = runTest {
        assertEquals(198, rangeOf(evRange("318", unit = 1)))
    }

    @Test
    fun evRangeWithoutUnitIsTakenAsMiles() = runTest {
        assertEquals(150, rangeOf(evRange("150", unit = null)))
    }

    @Test
    fun missingRangeIsNull() = runTest {
        assertNull(rangeOf(""))
        assertNull(rangeOf(""","evStatus":{"batteryStatus":80}"""))
        assertNull(rangeOf(""","evStatus":{"drvDistance":[]}"""))
    }

    @Test
    fun dteIsUsedWhenThereIsNoEvRange() = runTest {
        assertEquals(320, rangeOf(""","dte":{"value":320,"unit":3}"""))
        assertEquals(62, rangeOf(""","dte":{"value":100,"unit":1}"""))
    }
}
