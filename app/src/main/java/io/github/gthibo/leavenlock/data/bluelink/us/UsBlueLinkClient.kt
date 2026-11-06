package io.github.gthibo.leavenlock.data.bluelink.us

import io.github.gthibo.leavenlock.data.bluelink.BlueLinkClient
import io.github.gthibo.leavenlock.data.bluelink.model.CommandResult
import io.github.gthibo.leavenlock.data.bluelink.model.LockState
import io.github.gthibo.leavenlock.data.bluelink.model.Vehicle
import io.github.gthibo.leavenlock.data.bluelink.model.VehicleStatus
import io.github.gthibo.leavenlock.data.bluelink.model.kmToMiles
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.util.TimeZone
import java.time.Instant

/**
 * Experimental US client.
 * The access token stays in memory. The email + password are saved in [credentialStore]
 * (encrypted `SecureStore` in the app) after a successful sign-in, so the session survives
 * settings changes, app kills and reboots: a fresh client reloads them and signs in again.
 * Lock/unlock send real commands (see [sendDoorCommand]); the BlueLink PIN comes from
 * [pinProvider] (the encrypted `SecureStore` PIN captured at sign-in).
 */
class UsBlueLinkClient(
    private val http: HttpClient,
    private val pinProvider: () -> String? = { null },
    private val credentialStore: UsCredentialStore = UsCredentialStore.None,
    /** Activity-log line sink (diagnostics only; never pass account data or tokens). */
    private val diag: (String) -> Unit = {},
) : BlueLinkClient {

    private companion object {
        val TRANSACTION_HEADERS = listOf("tmsTid", "transactionId", "Xid")
        const val POLL_ATTEMPTS = 10
        const val POLL_INTERVAL_MS = 3_000L
        /** Hyundai distance unit code for kilometres (2 and 3 are miles). */
        const val UNIT_KM = 1
    }

    private val mutex = Mutex()
    private var username = ""
    private var password = ""
    private var accessToken = ""
    private var expiresAt = 0L
    private val vehicleHeaders = mutableMapOf<String, UsVehicle>()

    private data class UsVehicle(
        val vin: String,
        val generation: String,
    )

    private val base = "https://api.telematics.hyundaiusa.com"

    private fun headers(
        request: io.ktor.client.request.HttpRequestBuilder,
        authenticated: Boolean = true,
    ) {
        request.header("Accept", "application/json")
        request.header("User-Agent", "Mozilla/5.0")
        request.header("Origin", base)
        request.header("Referer", "$base/login")
        request.header("from", "SPA")
        request.header("to", "ISS")
        request.header("language", "0")
        request.header(
            "offset",
            (TimeZone.getDefault().getOffset(
                System.currentTimeMillis()
            ) / 3_600_000).toString()
        )
        request.header("refresh", "false")
        request.header("encryptFlag", "false")
        request.header("brandIndicator", "H")
        request.header(
            "client_id",
            "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920"
        )
        request.header("clientSecret", "v558o935-6nne-423i-baa8")
        if (authenticated) {
            request.header("username", username)
            request.header("accessToken", accessToken)
        }
    }

    private suspend fun responseObject(
        response: HttpResponse,
    ): JsonObject {
        if (response.status.value !in 200..299) {
            error("US API returned HTTP ${response.status.value}")
        }
        val result = Json.parseToJsonElement(
            response.bodyAsText()
        ).jsonObject
        if ("errorCode" in result) {
            error("US API reported an error; response body withheld.")
        }
        return result
    }

    private suspend fun authenticate() {
        val response = http.post("$base/v2/ac/oauth/token") {
            headers(this, authenticated = false)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("username", username)
                put("password", password)
            }.toString())
        }
        val body = responseObject(response)
        accessToken = body["access_token"]
            ?.jsonPrimitive?.contentOrNull
            ?: error("US login returned no access token")
        val seconds = body["expires_in"]
            ?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            ?: error("US login returned no token lifetime")
        expiresAt = System.currentTimeMillis() +
            (seconds * 1000).toLong()
    }

    override suspend fun loginWithPassword(
        username: String,
        password: String,
    ): CommandResult = mutex.withLock {
        this.username = username.trim()
        this.password = password
        accessToken = ""
        expiresAt = 0L
        vehicleHeaders.clear()
        try {
            authenticate()
            credentialStore.save(UsCredentials(this.username, password))
            CommandResult.Success("US session established")
        } catch (cancelled: CancellationException) {
            clearMemory()
            credentialStore.clear()
            throw cancelled
        } catch (_: Exception) {
            clearMemory()
            credentialStore.clear()
            CommandResult.Failure(
                "US sign-in failed. No credentials or response body logged."
            )
        }
    }

    override suspend fun isAuthenticated(): Boolean =
        mutex.withLock { restoreCredentials() }

    /** Loads saved credentials into memory if needed. True when credentials are available. */
    private fun restoreCredentials(): Boolean {
        if (password.isNotBlank()) return true
        val saved = credentialStore.load()
            ?.takeIf { it.username.isNotBlank() && it.password.isNotBlank() }
            ?: return false
        username = saved.username
        password = saved.password
        accessToken = ""
        expiresAt = 0L
        return true
    }

    override suspend fun ensureFreshSession(): Boolean =
        mutex.withLock {
            if (!restoreCredentials()) return@withLock false
            if (accessToken.isNotBlank() &&
                System.currentTimeMillis() < expiresAt - 60_000
            ) {
                return@withLock true
            }
            try {
                authenticate()
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }

    override suspend fun vehicles(): List<Vehicle> {
        check(ensureFreshSession()) { "US sign-in required" }
        val response = http.get(
            "$base/ac/v2/enrollment/details/$username"
        ) {
            headers(this)
        }
        val body = responseObject(response)
        val entries = body["enrolledVehicleDetails"] as? JsonArray
            ?: error("US API returned no vehicle list")

        return entries.mapNotNull { entry ->
            val details = entry.jsonObject["vehicleDetails"]
                ?.jsonObject ?: return@mapNotNull null

            fun text(key: String): String? =
                details[key]?.jsonPrimitive?.contentOrNull

            if (text("enrollmentStatus") == "CANCELLED") {
                return@mapNotNull null
            }

            val id = text("regid")
                ?: error("Vehicle registration ID missing")
            val vin = text("vin")
                ?: error("Vehicle VIN missing")

            vehicleHeaders[id] = UsVehicle(
                vin = vin,
                generation = text("vehicleGeneration") ?: "2",
            )

            Vehicle(
                id = id,
                vin = vin,
                nickname = text("nickName")
                    ?.takeIf { it.isNotBlank() } ?: "US Hyundai",
                model = text("modelCode") ?: "Hyundai",
                regDate = text("enrollmentDate"),
            )
        }
    }

    override suspend fun status(
        vehicleId: String,
        forceRefresh: Boolean,
    ): VehicleStatus {
        check(ensureFreshSession()) { "US sign-in required" }
        if (vehicleId !in vehicleHeaders) vehicles()
        val vehicle = vehicleHeaders[vehicleId]
            ?: error("Selected vehicle not found on US account")

        // Always cached in this first test, even when refresh is requested.
        val response = http.get(
            "$base/ac/v2/rcs/rvs/vehicleStatus"
        ) {
            headers(this)
            header("registrationId", vehicleId)
            header("gen", vehicle.generation)
            header("vin", vehicle.vin)
        }

        val body = responseObject(response)

        val status = body["vehicleStatus"]
            ?.jsonObject ?: error("US vehicle status missing")
        fun primitive(key: String): JsonPrimitive? =
            status[key] as? JsonPrimitive

        fun flag(value: JsonElement?): Boolean? {
            val item = value as? JsonPrimitive ?: return null
            return item.booleanOrNull ?: when (item.intOrNull) {
                0 -> false
                1 -> true
                else -> null
            }
        }

        fun anyOpen(key: String): Boolean? {
            val group = status[key] as? JsonObject ?: return null
            val values = listOf(
                "frontLeft", "frontRight", "backLeft", "backRight"
            ).map { flag(group[it]) }
            return when {
                values.any { it == true } -> true
                values.all { it == false } -> false
                else -> null
            }
        }

        val engine = flag(primitive("engine"))
            ?: error("US status omitted engine state")
        val ev = status["evStatus"] as? JsonObject
        val battery = status["battery"] as? JsonObject
        val sourceTimestamp = (status["dateTime"] as? JsonPrimitive)
            ?.contentOrNull
            ?.let { value ->
                runCatching {
                    Instant.parse(value).toEpochMilli()
                }.getOrNull()
            }
            ?: 0L
        return VehicleStatus(
            lockState = when (flag(primitive("doorLock"))) {
                true -> LockState.LOCKED
                false -> LockState.UNLOCKED
                null -> LockState.UNKNOWN
            },
            engineRunning = engine,
            batteryCharging = flag(ev?.get("batteryCharge")),
            // Timestamp supplied by the US status response; 0 means missing or invalid.
            timestamp = sourceTimestamp,
            reportedAtEpochMs = sourceTimestamp.takeIf { it > 0L },
            evBatteryPercent = (ev?.get("batteryStatus")
                as? JsonPrimitive)?.intOrNull,
            climateOn = flag(primitive("airCtrlOn")),
            twelveVoltPercent = (battery?.get("batSoc")
                as? JsonPrimitive)?.intOrNull,
            anyDoorOpen = anyOpen("doorOpen"),
            anyWindowOpen = anyOpen("windowOpen"),
            rangeMiles = rangeMiles(ev, status["dte"] as? JsonObject),
        )
    }

    /**
     * Remaining range in miles. EVs: `evStatus.drvDistance[0].rangeByFuel.totalAvailableRange`;
     * otherwise `dte` (as in bluelinky / hyundai_kia_connect_api). Each is `{value, unit}`; unit
     * codes per hyundai_kia_connect_api `DISTANCE_UNITS`: 1 = km, 2 or 3 = miles. A missing or
     * unknown unit is taken as miles (the US API's default; bluelinky ignores the unit).
     */
    private fun rangeMiles(ev: JsonObject?, dte: JsonObject?): Int? {
        val total = ((ev?.get("drvDistance") as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.let { it["rangeByFuel"] as? JsonObject }
            ?.let { it["totalAvailableRange"] as? JsonObject }
        val range = total?.takeIf { distanceValue(it) != null } ?: dte ?: return null
        val value = distanceValue(range) ?: return null
        return when ((range["unit"] as? JsonPrimitive)?.intOrNull) {
            UNIT_KM -> kmToMiles(value)
            else -> Math.round(value).toInt()
        }
    }

    private fun distanceValue(o: JsonObject): Double? =
        (o["value"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it >= 0.0 }

    override suspend fun lock(vehicleId: String): CommandResult =
        sendDoorCommand(vehicleId, lock = true)

    override suspend fun unlock(vehicleId: String): CommandResult =
        sendDoorCommand(vehicleId, lock = false)

    /**
     * US remote door command, matching `hyundai_kia_connect_api` (`lock_action` +
     * `check_action_status`) and `bluelinky` (`american.vehicle.ts`):
     * `POST /ac/v2/rcs/rdo/off` (lock) or `/on` (unlock) with the vehicle headers, the BlueLink
     * PIN in `blueLinkServicePin`, and a JSON body `{userName, vin}`. HTTP 200 (often with an
     * empty body) means queued; an `errorCode` in the body means rejected. The car's real result
     * is then polled at `GET /ac/v2/rmt/getRunningStatus` using the transaction id the command
     * response returns in a header (`tmsTid` / `transactionId` / `Xid`).
     */
    private suspend fun sendDoorCommand(
        vehicleId: String,
        lock: Boolean,
    ): CommandResult {
        val verb = if (lock) "lock" else "unlock"
        if (!ensureFreshSession()) return CommandResult.NotAuthenticated
        val pin = pinProvider()?.trim()?.takeIf { it.isNotEmpty() }
            ?: return CommandResult.Failure(
                "BlueLink PIN needed for US $verb — sign in again and enter your 4-digit PIN."
            )
        try {
            if (vehicleId !in vehicleHeaders) vehicles()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return CommandResult.Failure("Could not load the US vehicle list before $verb.")
        }
        val vehicle = vehicleHeaders[vehicleId]
            ?: return CommandResult.Failure("Selected vehicle not found on US account")

        val response = try {
            http.post("$base/ac/v2/rcs/rdo/${if (lock) "off" else "on"}") {
                vehicleCommandHeaders(this, vehicleId, vehicle, pin)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("userName", username)
                    put("vin", vehicle.vin)
                }.toString())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return CommandResult.Failure("Network error sending US $verb.")
        }

        when (response.status.value) {
            in 200..299 -> Unit
            401 -> {
                // Token rejected: force a fresh sign-in on the next call (a retry can recover).
                expiresAt = 0L
                return CommandResult.Failure("Hyundai rejected the session (HTTP 401) for $verb.")
            }
            429 -> return CommandResult.RateLimited
            else -> return CommandResult.Failure("Hyundai returned HTTP ${response.status.value} for $verb.")
        }

        bodyError(response.bodyAsText())?.let { return CommandResult.Failure(it) }

        val tid = TRANSACTION_HEADERS.firstNotNullOfOrNull { response.headers[it] }
            ?.takeIf { it.isNotBlank() }
            // No transaction id: queued, but the result can't be polled. The caller verifies.
            ?: return CommandResult.Success(if (lock) "Lock sent" else "Unlock sent")

        return pollCommandResult(vehicleId, vehicle, pin, tid, verb, lock)
    }

    private suspend fun pollCommandResult(
        vehicleId: String,
        vehicle: UsVehicle,
        pin: String,
        tid: String,
        verb: String,
        lock: Boolean,
    ): CommandResult {
        repeat(POLL_ATTEMPTS) {
            delay(POLL_INTERVAL_MS)
            val status = try {
                val response = http.get("$base/ac/v2/rmt/getRunningStatus") {
                    vehicleCommandHeaders(this, vehicleId, vehicle, pin)
                    header("tid", tid)
                    header("login_id", username)
                    header("service_type", "REMOTE_POLL")
                }
                if (response.status.value !in 200..299) null
                else Json.parseToJsonElement(response.bodyAsText())
                    .jsonObject["status"]?.jsonPrimitive?.contentOrNull
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null // Transient poll error or non-JSON body: keep polling.
            }
            when (status) {
                "SUCCESS" -> return CommandResult.Success(if (lock) "Locked" else "Unlocked")
                "ERROR" -> return CommandResult.Failure(
                    "Car reported the $verb failed — it may be on, a door may be open, or it has no signal."
                )
            }
        }
        val seconds = POLL_ATTEMPTS * POLL_INTERVAL_MS / 1000
        return CommandResult.Failure(
            "US $verb sent but the car didn't confirm within ${seconds}s. It may still go through."
        )
    }

    private fun vehicleCommandHeaders(
        request: io.ktor.client.request.HttpRequestBuilder,
        vehicleId: String,
        vehicle: UsVehicle,
        pin: String,
    ) {
        headers(request)
        request.header("registrationId", vehicleId)
        request.header("gen", vehicle.generation)
        request.header("vin", vehicle.vin)
        request.header("APPCLOUD-VIN", vehicle.vin)
        request.header("blueLinkServicePin", pin)
    }

    /** Hyundai's error message from a command body, or null if the body is empty / has no error. */
    private fun bodyError(body: String): String? {
        if (body.isBlank()) return null
        val json = runCatching { Json.parseToJsonElement(body) as? JsonObject }
            .getOrNull() ?: return null
        val code = json["errorCode"]?.jsonPrimitive?.contentOrNull ?: return null
        val message = json["errorMessage"]?.jsonPrimitive?.contentOrNull
            ?.take(120)?.takeIf { it.isNotBlank() } ?: "no message"
        return "Hyundai error $code: $message"
    }

    private fun clearMemory() {
        username = ""
        password = ""
        accessToken = ""
        expiresAt = 0L
        vehicleHeaders.clear()
    }

    override suspend fun clearSession() {
        mutex.withLock {
            clearMemory()
            credentialStore.clear()
        }
    }
}