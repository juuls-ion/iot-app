package com.specknet.pdiotapp.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import com.specknet.pdiotapp.utils.ThingyLiveData
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class ClassifyingActivity : AppCompatActivity() {


    private val MODEL_PATH = "model.tflite"


    private val tflite by lazy {
        Interpreter(
            FileUtil.loadMappedFile(this, MODEL_PATH))
    }

    // Data Stream Setup
    lateinit var respeckLiveUpdateReceiver: BroadcastReceiver
    lateinit var thingyLiveUpdateReceiver: BroadcastReceiver
    lateinit var looperRespeck: Looper
    lateinit var looperThingy: Looper
    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    val filterTestThingy = IntentFilter(Constants.ACTION_THINGY_BROADCAST)
    lateinit var stream: Array<FloatArray>
    private var bufferSize: Int = 50


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_classify)
        setupStream()

    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(respeckLiveUpdateReceiver)
        unregisterReceiver(thingyLiveUpdateReceiver)
        looperRespeck.quit()
        looperThingy.quit()
    }

    // Data Stream methods

    fun setupStream() {
        stream = Array(bufferSize) { FloatArray(6) }

        respeckLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {

                Log.i("thread",  "On " + Thread.currentThread().name)

                val action = intent.action
                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.d("Live", "onReceive(respeck): liveData = " + liveData)

                    // get all relevant intent contents
                    val x = liveData.accelX
                    val y = liveData.accelY
                    val z = liveData.accelZ

                    stream[bufferSize-1][0] = x
                    stream[bufferSize-1][1] = y
                    stream[bufferSize-1][2] = z

                    streamCallback()

                }
            }
        }

        thingyLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {

                Log.i("thread",  "On " + Thread.currentThread().name)

                val action = intent.action
                if (action == Constants.ACTION_THINGY_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.THINGY_LIVE_DATA) as ThingyLiveData
                    Log.d("Live", "onReceive(thingy): liveData = " + liveData)

                    // get all relevant intent contents
                    val x = liveData.accelX
                    val y = liveData.accelY
                    val z = liveData.accelZ

                    stream[bufferSize-1][3] = x
                    stream[bufferSize-1][4] = y
                    stream[bufferSize-1][5] = z

                    streamCallback()

                }
            }
        }

        val handlerThreadRespeck = HandlerThread("bgThreadRespeckLive")
        handlerThreadRespeck.start()
        looperRespeck = handlerThreadRespeck.looper
        val handlerRespeck = Handler(looperRespeck)
        this.registerReceiver(respeckLiveUpdateReceiver, filterTestRespeck, null, handlerRespeck)

        val handlerThreadThingy = HandlerThread("bgThreadThingyLive")
        handlerThreadThingy.start()
        looperThingy = handlerThreadThingy.looper
        val handlerThingy = Handler(looperThingy)
        this.registerReceiver(thingyLiveUpdateReceiver, filterTestThingy, null, handlerThingy)

    }

    fun streamCallback() {
        // Move to next item if array is full
        if (stream[stream.size-1].all{it != 0f}) {
            // Start from the end of the array and move each element to the left
            for (i in stream.indices.reversed().drop(1)) {
                stream[i - 1] = stream[i]
            }
            // Optionally set the last element to a default value (e.g., 0)
            stream[stream.size - 1] = FloatArray(6)
        }

        // Ensure not operating on a null array
        if (stream[0].all { it == 0f})
            return

        val output = classify(stream)

        Log.i("classify", "classified as " + output)

    }


    // Inference Methods


    private fun floatArrayToBuffer(floatArray: FloatArray): FloatBuffer? {
        val byteBuffer: ByteBuffer = ByteBuffer
            .allocateDirect(floatArray.size * 4)

        byteBuffer.order(ByteOrder.nativeOrder())

        val floatBuffer: FloatBuffer = byteBuffer.asFloatBuffer()

        floatBuffer.put(floatArray)
        floatBuffer.position(0)
        return floatBuffer
    }

    private fun floatArray2DToByteBuffer(floatArray: Array<FloatArray>): ByteBuffer {
        // Calculate the total size needed for the ByteBuffer
        val byteBuffer = ByteBuffer.allocateDirect(floatArray.size * floatArray[0].size * 4) // 4 bytes for each float
        byteBuffer.order(ByteOrder.nativeOrder()) // Set the byte order to native

        // Fill the ByteBuffer with data
        for (i in floatArray.indices) {
            for (j in floatArray[i].indices) {
                byteBuffer.putFloat(floatArray[i][j])
            }
        }
        byteBuffer.rewind() // Reset the position to the beginning
        return byteBuffer
    }


    fun classify(input: Array<FloatArray>) : FloatBuffer?{

        val inF = floatArray2DToByteBuffer(input)
        val outs = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        var outF = floatArrayToBuffer(outs)

        tflite.run(inF, outF)
        return outF
    }


}