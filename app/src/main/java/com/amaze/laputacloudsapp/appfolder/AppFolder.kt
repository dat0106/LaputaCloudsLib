package com.amaze.laputacloudsapp.appfolder

import android.os.Build
import android.os.Environment
import com.amaze.laputacloudsapp.appfolder.PhoneDriver.Companion.FALSE_ROOT
import com.amaze.laputacloudslib.*
import com.amaze.laputacloudslib.AbstractCloudPath.Companion.SEPARATOR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream

class PhoneFile(
    override val path: CloudPath
) : AbstractCloudFile() {
    val file = (path as PhonePath).toFile()

    override val name = file.name
    override val isDirectory = file.isDirectory
    override val isRootDirectory = path.sanitizedPath == SEPARATOR
    override val byteSize = file.length()

    override fun getParent(callback: suspend (AbstractCloudFile?) -> Unit) {
        val parentPath = file.parentFile?.toPhonePath()
        CoroutineScope(Dispatchers.Main).launch {
            callback(if (parentPath == null) null else PhoneFile(parentPath))
        }
    }

    override fun delete(callback: () -> Unit) {
        if(!file.delete()) {
            throw IOException("Delete failed")
        }

        callback()
    }

    override fun copyTo(
        newName: String,
        folder: AbstractCloudFile,
        callback: (AbstractCloudFile) -> Unit
    ) {
        folder as PhoneFile

        val targetFile = File(folder.file, newName)

        file.copyTo(targetFile)

        callback(PhoneFile(targetFile.toPhonePath()))
    }

    override fun moveTo(
        newName: String,
        folder: AbstractCloudFile,
        callback: (AbstractCloudFile) -> Unit
    ) {
        copyTo(newName, folder) {
            file.delete()

            folder as PhoneFile

            val targetFile = File(folder.file, newName)
            callback(PhoneFile(targetFile.toPhonePath()))
        }
    }

    override fun download(callback: (InputStream) -> Unit) {
        callback(file.inputStream())
    }

    override fun uploadHere(
        fileToUpload: AbstractCloudFile,
        onProgress: ((bytes: Long) -> Unit)?,
        callback: (uploadedFile: AbstractCloudFile) -> Unit
    ) {
        throw NotImplementedError("Use copyTo or moveTo")
    }

    override fun uploadHere(
        inputStream: InputStream,
        name: String,
        size: Long,
        onProgress: ((bytes: Long) -> Unit)?,
        callback: (uploadedFile: AbstractCloudFile) -> Unit
    ) {
        throw NotImplementedError("Use copyTo or moveTo")
    }

}

class PhoneDriver : AbstractFileStructureDriver() {
    companion object {
        val FALSE_ROOT = getStartingFile().canonicalPath

        @JvmStatic
        private fun getStartingFile(): File {
            lateinit var externalDir: File

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (isEmulator()) {
                    externalDir = File("/sdcard/")
                } else {
                    val externalFile = Environment.getExternalStorageDirectory()
                        ?: throw IOException("Failed to read files")
                    externalDir = File(externalFile.path)
                }
            } else {
                throw IllegalStateException("No support for Android Q")
            }

            externalDir.setReadable(true)
            return externalDir
        }

        @JvmStatic
        private fun isEmulator() = (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT)
    }

    override fun getRoot(): CloudPath {
        return PhonePath("/")
    }

    override suspend fun getFiles(path: CloudPath, callback: suspend (List<AbstractCloudFile>) -> Unit) {
        path as PhonePath

        callback(path.toFile().listFiles()!!.map {  it: File ->
            PhoneFile(it.toPhonePath())
        })
    }

    override suspend fun getFile(path: CloudPath, callback: suspend (AbstractCloudFile) -> Unit) {
        callback(PhoneFile(File(path.fullPath).toPhonePath()))
    }
}

fun File.toPhonePath(): PhonePath {
    return PhonePath(this.canonicalPath.substringAfter(FALSE_ROOT))
}

class PhonePath(path: String) : AbstractCloudPath<PhonePath>(path) {
    override val scheme: String = FALSE_ROOT

    fun toFile() = File(fullPath)
}

class PhoneAccount : AbstractAccount() {
    override suspend fun tryLogInAsync(callback: suspend (AbstractFileStructureDriver) -> Unit) {
        callback(PhoneDriver())
    }
}