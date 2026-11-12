/*
 * Adaptado de NewPipe (org.schabi.newpipe.util.potoken.JavaScriptUtil), GPLv3.
 */
package me.mjoacobf.openmusicstreaming.core.extractor.potoken

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

class PoTokenException(message: String) : Exception(message)

/** Se lanza si el WebView del sistema está roto (p. ej. JS demasiado antiguo). */
class BadWebViewException(message: String) : Exception(message)

fun buildExceptionForJsError(error: String): Exception =
    if (error.contains("SyntaxError")) BadWebViewException(error) else PoTokenException(error)

/** Convierte la respuesta del endpoint Create en un objeto embebible en JavaScript. */
fun parseChallengeData(rawChallengeData: String): String {
    val scrambled = JsonParser.array().from(rawChallengeData)

    val challengeData = if (scrambled.size > 1 && scrambled.isString(1)) {
        val descrambled = descramble(scrambled.getString(1))
        JsonParser.array().from(descrambled)
    } else {
        scrambled.getArray(0)
    }

    val messageId = challengeData.getString(0)
    val interpreterHash = challengeData.getString(3)
    val program = challengeData.getString(4)
    val globalName = challengeData.getString(5)
    val clientExperimentsStateBlob = challengeData.getString(7)

    val safeScript = challengeData.getArray(1, null)?.find { it is String }
    val trustedResourceUrl = challengeData.getArray(2, null)?.find { it is String }

    return JsonWriter.string(
        JsonObject.builder()
            .value("messageId", messageId)
            .`object`("interpreterJavascript")
            .value("privateDoNotAccessOrElseSafeScriptWrappedValue", safeScript)
            .value("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", trustedResourceUrl)
            .end()
            .value("interpreterHash", interpreterHash)
            .value("program", program)
            .value("globalName", globalName)
            .value("clientExperimentsStateBlob", clientExperimentsStateBlob)
            .done(),
    )
}

/** Devuelve el integrityToken como `Uint8Array` JS y su duración en segundos. */
fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
    val integrityTokenData = JsonParser.array().from(rawIntegrityTokenData)
    return base64ToU8(integrityTokenData.getString(0)) to integrityTokenData.getLong(1)
}

fun stringToU8(identifier: String): String = newUint8Array(identifier.toByteArray())

fun u8ToBase64(poToken: String): String =
    poToken.split(",")
        .map { it.toUByte().toByte() }
        .toByteArray()
        .toByteString()
        .base64()
        .replace("+", "-")
        .replace("/", "_")

private fun descramble(scrambledChallenge: String): String =
    base64ToByteString(scrambledChallenge)
        .map { (it + 97).toByte() }
        .toByteArray()
        .decodeToString()

private fun base64ToU8(base64: String): String = newUint8Array(base64ToByteString(base64))

private fun newUint8Array(contents: ByteArray): String =
    "new Uint8Array([" + contents.joinToString(separator = ",") { it.toUByte().toString() } + "])"

private fun base64ToByteString(base64: String): ByteArray {
    val base64Mod = base64
        .replace('-', '+')
        .replace('_', '/')
        .replace('.', '=')
    return (base64Mod.decodeBase64() ?: throw PoTokenException("Cannot base64 decode"))
        .toByteArray()
}
