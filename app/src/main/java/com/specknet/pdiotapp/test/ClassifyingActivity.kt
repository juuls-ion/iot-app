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
import com.specknet.pdiotapp.utils.Activity
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.CountUpTimer
import com.specknet.pdiotapp.utils.ExtraUtils
import com.specknet.pdiotapp.utils.FileManager
import com.specknet.pdiotapp.utils.IOutputEnum
import com.specknet.pdiotapp.utils.Model
import com.specknet.pdiotapp.utils.RESpeckLiveData
import com.specknet.pdiotapp.utils.Resp
import com.specknet.pdiotapp.utils.ThingyLiveData
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.FloatBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.pow


class ClassifyingActivity : AppCompatActivity() {

    val default_mean = listOf(0f ,0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f )
    // Model setup
    private val physical_model by lazy {
        Model(Interpreter(
            FileUtil.loadMappedFile(this,  "model.tflite")),
            6, 50, listOf(-0.052624739060104245f, -0.4981773556370512f, 0.05811940078095967f, -0.3793667556515435f, -0.025363258349146357f, 0.09048162019906163f) , ListOf(0.4842730884136554f, 0.5355855344330723f, 0.5617978830547341f, 0.5768714831322992f, 0.5292077937332008f, 0.6243277315595913f))
    }

    private val dyn_stat_model by lazy {
        Model(Interpreter(
            FileUtil.loadMappedFile(this,  "dynamic_static_model.tflite")),
            6, 50, List(6) {0f} , List(6) {0f})
    }

    private val dyn_model by lazy {
        Model(Interpreter(
            FileUtil.loadMappedFile(this,  "dynamic_model.tflite")),
            6, 50, List(6) {0f} , List(6) {0f})
    }

    private val stat_model by lazy {
        Model(Interpreter(
            FileUtil.loadMappedFile(this,  "static_model.tflite")),
            6, 50, List(6) {0f} , List(6) {0f})
    }


    private val resp_model by lazy {
        Model(Interpreter(
            FileUtil.loadMappedFile(this, "resp_model.tflite")),
            3, 50, List(3) {0f} , List(3) {0f})
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

    lateinit var fileManager: FileManager

    // Background classification task
    var saveActivityData: Boolean = false
    val sampleSize = 3  // defines the sample size to average over

    // physical actions
    var physicalActivity = Array(sampleSize) { Activity.UNDEFINED}
    // Gets the mode of the physical activity array
    var curAction: () -> Activity = {physicalActivity.groupingBy { it }.eachCount().maxByOrNull {it.value}?.key?:Activity.UNDEFINED}

    // respiratory signals
    var respSignals = Array(sampleSize) { Resp.UNDEFINED }
    // Gets the mode of the physical activity array
    var curResp: () -> Resp = {respSignals.groupingBy { it }.eachCount().maxByOrNull {it.value}?.key?:Resp.UNDEFINED}

    // Initialised a background task to update the two action arrays
    lateinit var mainHandler: Handler
    private val updateClassifyTextTask = object : Runnable {
        override fun run() {
            updatePhysicalActivity()
//            updateRespSignals()
            save(curAction(), curResp())
            mainHandler.postDelayed(this, 500)
        }
    }


    lateinit var scheduledUpdate: CountUpTimer

    // Buttons
    lateinit var startRecordingButton: Button
    lateinit var stopRecordingButton: Button


    // Override functions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileManager = FileManager(getExternalFilesDir(null), Activity)

        setContentView(R.layout.activity_classify)

        setupClickListeners()


        scheduledUpdate = object: CountUpTimer(1000) {
            override fun onTick(elapsedTime: Long) {
                updateClassifyText()
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

                    updateStream()
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

                    updateStream()

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

        fileManager.close()

        looperRespeck.quit()
        looperThingy.quit()
        scheduledUpdate.stop()
    }


    // Setup methods

    fun setupClickListeners() {
        startRecordingButton = findViewById(R.id.classify_start_button)
        stopRecordingButton = findViewById(R.id.classify_stop_button)

        startRecordingButton.setOnClickListener {
            saveActivityData = true
        }

        stopRecordingButton.setOnClickListener {
            save()
            saveActivityData = false
        }

    }


    // Update methods

    fun updatePhysicalActivity() {
        ExtraUtils.moveLeft(physicalActivity, Activity.UNDEFINED)
        physicalActivity[physicalActivity.size-1] = getCurrentAction()
    }

    fun updateRespSignals() {
        ExtraUtils.moveLeft(respSignals, Resp.UNDEFINED)
        respSignals[respSignals.size-1] = getCurrentResp()
    }

    fun updateClassifyText(){
        val physicalText = findViewById<TextView>(R.id.physical_classify_text)
        val respText = findViewById<TextView>(R.id.resp_classify_text)

        physicalText.setText(curAction().toStringResource())
        respText.setText(curResp().toStringResource())
    }

    // Functionality

    fun updateStream() {
        Log.d("stream", "streamCallback: stream current is " + stream[stream.size-1].joinToString(", "))
        // Move to next item if array is full
        if (stream[stream.size-1].all{it != 0f}) {
            Log.d("stream", "streamCallback: Attempting rolling window")
            Log.d("stream-data", "" + stream[stream.size-1].joinToString(","))
            stream = ExtraUtils.moveLeft(stream, FloatArray(inputSize))
        }

        Log.d("stream", "streamCallback: Moved data, current is "+ stream[stream.size-1].joinToString(", "))

    }

    // Action recording and other

    fun getCurrentAction() : Activity {
        Log.d("classify", "updateClassifyText: Called")

        // Check dynamic or static
        var out = dyn_stat_model.classify(stream, FloatBuffer.allocate(11)).array()
        Log.d("classify-act", out.joinToString(", "))
        if (out[0] > out[1]) {
            Log.d("classify-act", "dynamic")
            out = dyn_model.classify(stream, FloatBuffer.allocate(6)).array()
        }
        else {
            Log.d("classify-act", "static")
            out = stat_model.classify(stream, FloatBuffer.allocate(5)).array()
        }

//        var out = physical_model.classify(stream, FloatBuffer.allocate(11)).array()

        if (stream[0].all { it == 0f })
            return Activity.UNDEFINED

        Log.d("classify", "updateClassifyText: classified! " + out.joinToString(", "))

        return Activity.fromOneHot(out)
    }

    // Remember that the stream may be different here
    fun getCurrentResp() : Resp {
        Log.d("classify", "updateClassifyText: Called")

        if (Activity.isDynamic(curAction()) || stream[0].all { it == 0f })
            return Resp.UNDEFINED

        val outB = FloatBuffer.allocate( 11)
        // builds the input as a thre first three elements of each item in the list

        val out = resp_model.classify(stream, outB).array()

        Log.d("classify", "updateClassifyText: classified! " + out.joinToString(", "))

        return Resp.fromOneHot(out)
    }



    fun save(act: Activity = Activity.UNDEFINED,
             resp: Resp = Resp.UNDEFINED) {

        if (!saveActivityData)
            return

        fileManager.enum = Activity
        fileManager.write(act)
        fileManager.enum = Resp
        fileManager.write(resp)

    }

}