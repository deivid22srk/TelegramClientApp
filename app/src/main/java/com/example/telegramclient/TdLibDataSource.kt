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
            C.LENGTH_UNSET.toLong() 
        } else {
            dataSpec.length
        }

        // TdApi.DownloadFile(int fileId, int priority, long offset, long limit, boolean synchronous)
        client.send(TdApi.DownloadFile(fileId, 32, position, if (length == C.LENGTH_UNSET.toLong()) 0L else length, false)) { }

        opened = true
        transferStarted(dataSpec)
        bytesRemaining = length
        return if (length == C.LENGTH_UNSET.toLong()) C.LENGTH_UNSET.toLong() else length
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val currentPosition = dataSpec!!.position + (if (dataSpec!!.length != C.LENGTH_UNSET.toLong()) (dataSpec!!.length - bytesRemaining) else 0L)
        val countToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong() && bytesRemaining > 0) min(readLength.toLong(), bytesRemaining) else readLength.toLong()

        var bytesRead = 0
        val lock = Object()
        var resultData: ByteArray? = null

        // TdApi.ReadFilePart(int fileId, long offset, long count)
        client.send(TdApi.ReadFilePart(fileId, currentPosition, countToRead)) { result ->
            if (result is TdApi.FilePart) {
                resultData = result.data
            }
            synchronized(lock) { lock.notify() }
        }

        synchronized(lock) {
            if (resultData == null) lock.wait(1000) 
        }

        resultData?.let {
            val actualRead = min(it.size.toLong(), countToRead).toInt()
            System.arraycopy(it, 0, buffer, offset, actualRead)
            bytesRead = actualRead
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining -= actualRead
            }
            bytesTransferred(actualRead)
        }

        return if (bytesRead > 0) bytesRead else 0
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
