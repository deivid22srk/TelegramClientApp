package com.example.telegramclient

import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.min

class TdLibDataSource(
    private val client: Client,
    private val fileId: Int
) : BaseDataSource(true) {

    private var dataSpec: DataSpec? = null
    private var opened = false
    private var bytesRemaining: Long = 0
    private var totalFileSize: Long = C.LENGTH_UNSET.toLong()
    private var currentPosition: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        val position = dataSpec.position

        // First, get file info to know total size
        val latch = CountDownLatch(1)
        client.send(TdApi.GetFile(fileId)) { result ->
            if (result is TdApi.File) {
                totalFileSize = result.size
            }
            latch.countDown()
        }
        latch.await(2, TimeUnit.SECONDS)

        val length = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            if (totalFileSize != C.LENGTH_UNSET.toLong()) totalFileSize - position else C.LENGTH_UNSET.toLong()
        } else {
            dataSpec.length
        }

        Log.d("TdLibDataSource", "Opening file $fileId at $position, length $length (total $totalFileSize)")

        // Request TDLib to start downloading this part
        // priority 32 is high for streaming
        // We use position as offset and length as limit
        // Using a 10MB chunk size for pre-fetching when opening/seeking
        val prefetchSize = 10 * 1024 * 1024L
        client.send(TdApi.DownloadFile(fileId, 32, position, prefetchSize, false)) { }

        opened = true
        transferStarted(dataSpec)
        bytesRemaining = length
        currentPosition = position
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val countToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong() && bytesRemaining > 0) {
            min(readLength.toLong(), bytesRemaining)
        } else {
            readLength.toLong()
        }

        var bytesRead = 0
        var resultData: ByteArray? = null

        // We might need to retry if the part isn't downloaded yet
        var attempts = 0
        while (resultData == null && attempts < 15) {
            val latch = CountDownLatch(1)
            client.send(TdApi.ReadFilePart(fileId, currentPosition, countToRead)) { result ->
                if (result is TdApi.Data) {
                    resultData = result.data
                } else {
                    // If failed, make sure the file is still being downloaded
                    // Using a larger chunk size for pre-fetching when seeking
                    // 4MB prefetch
                    val downloadSize = if (countToRead < 4 * 1024 * 1024) 4 * 1024 * 1024L else countToRead
                    client.send(TdApi.DownloadFile(fileId, 32, currentPosition, downloadSize, false)) { }
                }
                latch.countDown()
            }
            // Increase wait time slightly
            latch.await(500L + (attempts * 200), TimeUnit.MILLISECONDS)
            if (resultData == null) {
                attempts++
                Log.w("TdLibDataSource", "Read failed for $fileId at $currentPosition, attempt $attempts")

                // Periodically re-send DownloadFile to ensure it's high priority
                if (attempts % 3 == 0) {
                     client.send(TdApi.DownloadFile(fileId, 32, currentPosition, 0, false)) { }
                }
            }
        }

        resultData?.let {
            val actualRead = min(it.size.toLong(), countToRead).toInt()
            System.arraycopy(it, 0, buffer, offset, actualRead)
            bytesRead = actualRead

            currentPosition += actualRead
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining -= actualRead
            } else if (totalFileSize != C.LENGTH_UNSET.toLong()) {
                // If length was unset, we check against total size
                if (currentPosition >= totalFileSize) bytesRemaining = 0
            }
            bytesTransferred(actualRead)
        }

        return if (bytesRead > 0) bytesRead else C.RESULT_END_OF_INPUT
    }

    override fun getUri() = dataSpec?.uri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        dataSpec = null
    }
}

class TdLibDataSourceFactory(
    private val client: Client,
    private val fileId: Int
) : androidx.media3.datasource.DataSource.Factory {
    override fun createDataSource(): androidx.media3.datasource.DataSource {
        return TdLibDataSource(client, fileId)
    }
}
