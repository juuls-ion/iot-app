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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.ExtraUtils
import com.specknet.pdiotapp.utils.CountUpTimer
import com.specknet.pdiotapp.utils.RESpeckLiveData
import com.specknet.pdiotapp.utils.ThingyLiveData
import org.apache.commons.lang3.ObjectUtils.Null
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date


enum class Activity(val value: Int) {
    ASCENDING(0),
    DESCENDING(1),
    LYING_BACK(2),
    LYING_LEFT(3),
    LYING_RIGHT(4),
    LYING_STOMACH(5),
    MISC(6),
    WALKING(7),
    RUNNING(8),
    SHUFFLE(9),
    SITTING_STANDING(10),
    UNDEFINED(11);

    companion object {
        fun find(value: Int?): Activity? = Activity.values().find { it.value == value }
    }
}


class ClassifyingActivity : AppCompatActivity() {


    private val MODEL_PATH = "model.tflite"


    private val tflite by lazy {
        Interpreter(
            FileUtil.loadMappedFile(this, MODEL_PATH))
    }

    // Data Stream Setup
    lateinit var respeckReceiver: BroadcastReceiver
    lateinit var thingyReceiver: BroadcastReceiver
    lateinit var looperRespeck: Looper
    lateinit var looperThingy: Looper

    val respeckFilterTest = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    val thingyFilterTest = IntentFilter(Constants.ACTION_THINGY_BROADCAST)

    lateinit var stream: Array<FloatArray>
    private var bufferSize: Int = 51
    private var inputSize: Int = 3

    // Classification Task

    var saveActivityData: Boolean = true
    var curAction: Activity = Activity.UNDEFINED
    lateinit var mainHandler: Handler
    private val updateClassifyTextTask = object : Runnable {
        override fun run() {

            curAction = getCurrentAction()
            updateClassifyText(curAction)
            saveAction(curAction)

            mainHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_classify)

        stream = Array(bufferSize) { FloatArray(inputSize) }

        respeckReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    val liveData = intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.d("respeckLive", "onReceive: liveData = " + liveData)

//                    stream[stream.size-1][0] = liveData.accelX
//                    stream[stream.size-1][1] = liveData.accelY
//                    stream[stream.size-1][2] = liveData.accelZ

                    Log.d("respeckLive", "onReceive: Updated Stream")

                    streamCallback()
                }
            }
        }

        val handlerThreadRespeck = HandlerThread("bgThreadRespeckClassify")
        handlerThreadRespeck.start()
        looperRespeck = handlerThreadRespeck.looper
        val handlerRespeck = Handler(looperRespeck)
        this.registerReceiver(respeckReceiver, respeckFilterTest, null, handlerRespeck)


        thingyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                val action = intent.action

                if (action == Constants.ACTION_THINGY_BROADCAST) {

                    val liveData = intent.getSerializableExtra(Constants.THINGY_LIVE_DATA) as ThingyLiveData
                    Log.d("thingyLive", "onReceive: thingyLiveData = " + liveData)

                    stream[stream.size-1][0] = liveData.accelX
                    stream[stream.size-1][1] = liveData.accelY
                    stream[stream.size-1][2] = liveData.accelZ

                    Log.d("thingyLive", "onReceive: Updated Stream")

                    streamCallback()

                }
            }
        }

        val handlerThreadThingy = HandlerThread("bgThreadThingyClassify")
        handlerThreadThingy.start()
        looperThingy = handlerThreadThingy.looper
        val handlerThingy = Handler(looperThingy)
        this.registerReceiver(thingyReceiver, thingyFilterTest, null, handlerThingy)



        mainHandler = Handler(Looper.getMainLooper())
        mainHandler.postDelayed(updateClassifyTextTask, 0)
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(respeckReceiver)
        unregisterReceiver(thingyReceiver)
        looperRespeck.quit()
        looperThingy.quit()

    }

    // Data Stream methods

    fun streamCallback() {
        Log.d("stream", "streamCallback: stream current is " + stream[stream.size-1].joinToString(", "))
        // Move to next item if array is full
        if (stream[stream.size-1].all{it != 0f}) {
            Log.d("stream", "streamCallback: Attempting rolling window")
            stream = ExtraUtils.moveLeft(stream, inputSize)
        }

        Log.d("stream", "streamCallback: Moved data, current is "+stream[stream.size-1].joinToString(", "))

    }

    // Update Methods

    fun getCurrentAction() : Activity {
        Log.d("classify", "updateClassifyText: Called")

        if (stream[0].all { it == 0f })
            return Activity.UNDEFINED

        val outB = FloatBuffer.allocate(4 * 11)
        val out = ExtraUtils.classify(stream, outB, tflite)!!.array()

        Log.d("classify", "updateClassifyText: classified! " + out.joinToString(", "))

        return ExtraUtils.fromOneHot(out)?:Activity.UNDEFINED
    }

    fun updateClassifyText(action: Activity?){
        var newText: Int

        when(action){
            Activity.ASCENDING -> newText = R.string.activity_ascending
            Activity.DESCENDING -> newText = R.string.activity_descending
            Activity.LYING_BACK -> newText = R.string.activity_lying_back
            Activity.LYING_LEFT -> newText = R.string.activity_lying_left
            Activity.LYING_RIGHT -> newText = R.string.activity_lying_right
            Activity.LYING_STOMACH -> newText = R.string.activity_lying_stomach
            Activity.WALKING -> newText = R.string.activity_walking
            Activity.MISC -> newText = R.string.activity_misc
            Activity.RUNNING -> newText = R.string.activity_running
            Activity.SHUFFLE -> newText = R.string.activity_shuffle
            Activity.SITTING_STANDING -> newText = R.string.activity_sitting_standing
            else -> newText = R.string.activity_undefined
        }

        val classifyText = findViewById<TextView>(R.id.classify)
        classifyText.setText(newText)

    }

    fun getFile(): File {
        // Initialised intended filename
        val time = Calendar.getInstance().time
        val formatter = SimpleDateFormat("yyyy-MM-dd")
        var filename = "recording-"+formatter.format(time)+".csv"

        var path = getExternalFilesDir(null)   //get file directory for this package

        Log.d("save", "getFile: Initiliased filename as "+ filename)

        //create fileOut object
        return File(path, filename)
    }

    fun setupFile() {
        val file = getFile()
        // Creates a new recording file with the correct formatting if it doesn't already exist
        if (file.exists())
            return

        val HEADER = "TIMESTAMP, ACTIVITY\n"
        file.createNewFile()
        file.appendText(HEADER)
        Log.d("save", "Successfully wrote 1 line to file")
    }


    fun writeLine(action: Activity) {
        val file = getFile()
        if (!file.exists())
            return

        var row = ""

        val time = Calendar.getInstance().time
        val formatter = SimpleDateFormat("HH:mm:ss")
        val now = formatter.format(time)

        row += now
        row += ","
        row += action
        row += "\n"

        file.appendText(row)

        Log.d("save", "Wrote " + row.dropLast(1) + " to file")
    }

    fun saveAction(action: Activity){
        if (!saveActivityData)
            return

        setupFile()

        writeLine(action)

    }

    // Inference Methods




}