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
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarEntry

import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.RecordingActivity
import com.specknet.pdiotapp.bluetooth.ConnectingActivity
import com.specknet.pdiotapp.utils.ActivityEntry
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
import java.util.concurrent.TimeUnit

/**
 * Enum for managing physical activities
 * */
enum class Activity(val value: Int) {
    UNDEFINED(-1),
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
    SITTING_STANDING(10);

    override fun toString(): String {
        return when(this) {
            ASCENDING -> "Ascending"
            DESCENDING -> "Descending"
            LYING_BACK -> "Lying Back"
            LYING_LEFT -> "Lying Left"
            LYING_RIGHT -> "Lying Right"
            LYING_STOMACH -> "Lying Stomach"
            WALKING -> "Walking"
            MISC -> "Misc"
            RUNNING -> "Running"
            SHUFFLE -> "Shuffling"
            SITTING_STANDING -> "Sitting/Standing"
            else -> "Undefined"
        }
    }

    companion object {
        fun find(value: Int?): Activity = Activity.values().find { it.value == value }?:Activity.UNDEFINED

        fun parse(value: String): Activity = find(value.toInt())

        fun toStringResource(value: Activity?): Int {
            return when(value){
                ASCENDING -> R.string.activity_ascending
                DESCENDING -> R.string.activity_descending
                LYING_BACK -> R.string.activity_lying_back
                LYING_LEFT -> R.string.activity_lying_left
                LYING_RIGHT -> R.string.activity_lying_right
                LYING_STOMACH -> R.string.activity_lying_stomach
                WALKING -> R.string.activity_walking
                MISC -> R.string.activity_misc
                RUNNING -> R.string.activity_running
                SHUFFLE -> R.string.activity_shuffle
                SITTING_STANDING -> R.string.activity_sitting_standing
                else -> R.string.activity_undefined
            }
        }
    }
}


class ClassifyingActivity : AppCompatActivity() {


    // Model setup
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
    private var inputSize: Int = 6


    // Background classification Task

    var saveActivityData: Boolean = false
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


    // Bar Chart
    var x_vals = buildList {
        for (i in IntRange(1, 11))
            add(""+Activity.find(i))
    }
    lateinit var data: BarDataSet
    lateinit var chart: BarChart
    lateinit var scheduledUpdate: CountUpTimer

    // Buttons
    lateinit var startRecordingButton: Button
    lateinit var stopRecordingButton: Button


    // Override functions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_classify)

        setupFile()

        setupClickListeners()

        setupGraph()

        scheduledUpdate = object: CountUpTimer(1000) {
            override fun onTick(elapsedTime: Long) {
                updateGraph()
            }
        }
        scheduledUpdate.start()

        stream = Array(bufferSize) { FloatArray(inputSize) }

        respeckReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    val liveData = intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.d("respeckLive", "onReceive: liveData = " + liveData)

                    stream[stream.size-1][0] = liveData.accelX
                    stream[stream.size-1][1] = liveData.accelY
                    stream[stream.size-1][2] = liveData.accelZ

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

                    stream[stream.size-1][3] = liveData.accelX
                    stream[stream.size-1][4] = liveData.accelY
                    stream[stream.size-1][5] = liveData.accelZ

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
        saveAction(Activity.UNDEFINED)
        looperRespeck.quit()
        looperThingy.quit()
        scheduledUpdate.stop()
    }


    // Setup methods

    fun setupGraph() {
        chart = findViewById(R.id.bar_chart)
        var entries: List<BarEntry> = buildList {

            for(x in x_vals.indices) {
                var entry = BarEntry(x.toFloat(), 0f)
                add(entry)
            }

        }

        data = BarDataSet(entries, "data")
        data.stackLabels = x_vals.toTypedArray()


        chart.setFitBars(true)
        chart.data = BarData(data)
    }


    fun setupClickListeners() {
        startRecordingButton = findViewById(R.id.classify_start_button)
        stopRecordingButton = findViewById(R.id.classify_stop_button)

        startRecordingButton.setOnClickListener {
            saveActivityData = true
        }

        stopRecordingButton.setOnClickListener {
            saveAction(Activity.UNDEFINED)
            saveActivityData = false
        }

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

    // Update methods

    fun updateClassifyText(action: Activity?){
        var newText: Int = Activity.toStringResource(action)
        val classifyText = findViewById<TextView>(R.id.classify)
        classifyText.setText(newText)

    }

    fun updateGraph() {
        val entriesRaw = loadFile()
        val actMap = mutableMapOf<Activity, Float>()
        var value: Float
        var diff: Float

        Log.d("graph", "Initialised variables")

        // Get sum of activities
        for (entry in entriesRaw) {
            diff = TimeUnit.MILLISECONDS.toMinutes(entry.end.time - entry.start.time).toFloat()
            actMap[entry.action] = (actMap.get(entry.action) ?: 0f) + diff
        }

        Log.d("graph", "Summed activities")

        // Build data set to use
        val labels = mutableListOf<String>()
        var index = 0
        val entries = buildList<BarEntry> {
            for(x in x_vals.indices) {
                value = actMap.get(Activity.find(x))?:-1f

                if (Activity.find(x) == Activity.UNDEFINED ||
                    value == -1f)
                    continue
                Log.d("graph", "added bar " + Activity.find(x).toString())
                add(BarEntry(index.toFloat(), value, Activity.find(x).toString()))
                index ++
                labels.add(Activity.find(x).toString())
            }

        }

        data.values = entries
        data.stackLabels = labels.toTypedArray()

        Log.d("graph", "Set data")

        chart.setFitBars(true)
        chart.data = BarData(data)
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chart.invalidate()
    }

    // Functionality

    fun streamCallback() {
        Log.d("stream", "streamCallback: stream current is " + stream[stream.size-1].joinToString(", "))
        // Move to next item if array is full
        if (stream[stream.size-1].all{it != 0f}) {
            Log.d("stream", "streamCallback: Attempting rolling window")
            stream = ExtraUtils.moveLeft(stream, inputSize)
        }

        Log.d("stream", "streamCallback: Moved data, current is "+stream[stream.size-1].joinToString(", "))

    }

    // Action recording and other

    fun getCurrentAction() : Activity {
        Log.d("classify", "updateClassifyText: Called")

        if (stream[0].all { it == 0f })
            return Activity.UNDEFINED

        val outB = FloatBuffer.allocate(4 * 11)
        val out = ExtraUtils.classify(stream, outB, tflite)!!.array()

        Log.d("classify", "updateClassifyText: classified! " + out.joinToString(", "))

        return ExtraUtils.fromOneHot(out)?:Activity.UNDEFINED
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
        row += action.value
        row += "\n"

        if (!file.readLines().last().contains(""+action.value))
            file.appendText(row)

        Log.d("save", "Wrote " + row.dropLast(1) + " to file")
    }


    fun saveAction(action: Activity){
        if (!saveActivityData)
            return

        setupFile()

        writeLine(action)

    }


    fun loadFile(): List<ActivityEntry> {
        Log.d("load", "Checking file ...")

        val file = getFile()

        if (!file.exists())
            return emptyList()
        Log.d("load", "Loading file ...")

        val formatter = SimpleDateFormat("HH:mm:ss")
        var st = Calendar.getInstance().time
        var end: Date
        var action: Activity
        var values: List<String>

        var entries = buildList {

            for (line in file.readLines().drop(1).reversed()) {
                values = line.split(",")
                end = st
                st = formatter.parse(values[0])?:st
                action = Activity.parse(values[1])

                if(end.before(st))
                    add(ActivityEntry(action, end, st))
                else
                    add(ActivityEntry(action, st, end))


            }
        }

        return entries
    }







}