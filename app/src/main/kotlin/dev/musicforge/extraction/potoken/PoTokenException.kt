// Vendored from Metrolist (github.com/MetrolistGroup/Metrolist, GPL-3.0) at 6d08504, adapted for MusicForge.
package dev.musicforge.extraction.potoken

class PoTokenException(message: String) : Exception(message)

// to be thrown if the WebView provided by the system is broken
class BadWebViewException(message: String) : Exception(message)

fun buildExceptionForJsError(error: String): Exception {
    return if (error.contains("SyntaxError"))
        BadWebViewException(error)
    else
        PoTokenException(error)
}
