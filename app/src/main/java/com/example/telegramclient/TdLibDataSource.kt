package com.example.telegramclient

import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import kotlin.math.min

class TdLibDataSource(
    private val client: Client,
    private val fileId: Int
) : BaseDataSource(true) {

    private var dataSpec: DataSpec? = null
    private var opened = false
    private var bytesRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        val position = dataSpec.position
        val length = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            Long.MAX_VALUE 
        } else {
            dataSpec.length
        }

        // Prioritize downloading this part
        client.send(TdApi.DownloadFile(fileId, 32, position.toInt(), length.toInt(), false)) { }

        opened = true
        transferStarted(dataSpec)
        bytesRemaining = length
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val currentPosition = dataSpec!!.position + (dataSpec!!.length - bytesRemaining)
        val countToRead = min(readLength.toLong(), bytesRemaining).toInt()

        var bytesRead = 0
        val lock = Object()
        var resultData: ByteArray? = null

        // Try to read the part. In a real app, we should wait for UpdateFile if not ready.
        client.send(TdApi.ReadFilePart(fileId, currentPosition.toInt(), countToRead)) { result ->
            if (result is TdApi.FilePart) {
                resultData = result.data
            }
            synchronized(lock) { lock.notify() }
        }

        synchronized(lock) {
            if (resultData == null) lock.wait(1000) // Wait up to 1s for data
        }

        resultData?.let {
            val actualRead = min(it.size, countToRead)
            System.arraycopy(it, 0, buffer, offset, actualRead)
            bytesRead = actualRead
            bytesRemaining -= actualRead
            bytesTransferred(dataSpec!!, actualRead)
        }

        return if (bytesRead > 0) bytesRead else 0
    }

    override fun getUri() = dataSpec?.uri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded(dataSpec!!)
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
