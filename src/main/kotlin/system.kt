import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch

val platform: Platform by lazy {
    when {
        SystemInfo.isLinux -> Platform.LINUX
        SystemInfo.isMac -> Platform.MAC
        SystemInfo.isWindows && CpuArch.isIntel32() -> Platform.WIN32
        SystemInfo.isWindows && CpuArch.isIntel64() -> Platform.WIN64
        else -> throw IllegalStateException("Unable to detect system platform")
    }
}

enum class Platform {
    LINUX,
    MAC,
    WIN32,
    WIN64
}

private enum class OS {
    WINDOWS, UNIX
}

private val os: OS by lazy {
    if (System.getProperty("os.name").toLowerCase().contains("win"))
        OS.WINDOWS
    else
        OS.UNIX
}

const val SPACE_ENCODED: String = "%20"
const val COLON_ENCODED: String = "%3A"
const val HASH_ENCODED: String = "%23"
const val URI_FILE_BEGIN = "file:"
const val WINDOWS_NETWORK_FILE_BEGIN = "file:////"
const val URI_PATH_SEP: Char = '/'
const val URI_VALID_FILE_BEGIN: String = "file:///"

fun sanitizeURI(uri: String?): String? {
    if (uri == null) {
        return null
    }
    if (uri.startsWith(WINDOWS_NETWORK_FILE_BEGIN)) {
        return uri.replace("file:////", "file://")
    } else if (!uri.startsWith(URI_FILE_BEGIN)) {
        // LOG.warn("Malformed uri : " + uri)
        return uri // Probably not an uri
    } else {
        val reconstructed = StringBuilder()
        var uriCp = uri.replace(" ", SPACE_ENCODED) // Don't trust servers
        uriCp = uriCp.drop(URI_FILE_BEGIN.length).dropWhile { c -> c == URI_PATH_SEP }
        reconstructed.append(URI_VALID_FILE_BEGIN)

        return if (os == OS.UNIX) {
            reconstructed.append(uriCp).toString()
        } else {
            reconstructed.append(uriCp.takeWhile { c -> c != URI_PATH_SEP })
            val driveLetter = reconstructed[URI_VALID_FILE_BEGIN.length]
            if (driveLetter.isLowerCase()) {
                reconstructed.setCharAt(URI_VALID_FILE_BEGIN.length, driveLetter.toUpperCase())
            }
            if (reconstructed.endsWith(COLON_ENCODED)) {
                reconstructed.delete(reconstructed.length - 3, reconstructed.length)
            }
            if (!reconstructed.endsWith(":")) {
                reconstructed.append(":")
            }
            reconstructed.append(uriCp.dropWhile { c -> c != URI_PATH_SEP }).toString()
        }
    }
}
