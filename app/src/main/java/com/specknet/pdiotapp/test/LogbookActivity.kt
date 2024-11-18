package com.specknet.pdiotapp.test

import android.os.Bundle
import android.text.format.Time
import android.util.Log
import android.widget.DatePicker
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Activity
import com.specknet.pdiotapp.utils.ActivityEntry
import com.specknet.pdiotapp.utils.CountUpTimer
import com.specknet.pdiotapp.utils.FileManager
import com.specknet.pdiotapp.utils.IOutputEnum
import com.specknet.pdiotapp.utils.IOutputEnumCompanion
import com.specknet.pdiotapp.utils.Resp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit


class LogbookActivity: AppCompatActivity() {

    lateinit var actChart: BarChart
    lateinit var respChart: BarChart
    lateinit var logbook_names: TextView
    lateinit var logbook_values: TextView
    lateinit var datePicker: DatePicker
    var date: () -> Date = { datePicker.getDate() }
    lateinit var fileManager: FileManager

    lateinit var data: BarDataSet

    lateinit var scheduledUpdate: CountUpTimer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileManager = FileManager(getExternalFilesDir(null), Activity)

        setContentView(R.layout.activity_logbook)
        actChart = findViewById(R.id.actChart)
        respChart = findViewById(R.id.respChart)
        logbook_names = findViewById(R.id.logbook_names)
        logbook_values = findViewById(R.id.logbook_values)

        datePicker = findViewById(R.id.date_picker)
        val today = Calendar.getInstance()
        datePicker.init(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)) {
                view, year, month, day ->
        }

        setupGraph(actChart, Activity)
        setupGraph(respChart, Resp)

        scheduledUpdate = object: CountUpTimer(1000) {
            override fun onTick(elapsedTime: Long) {
                updateGraph(actChart, Activity)
                updateGraph(respChart, Resp)
                updateLogbook()
            }
        }
        scheduledUpdate.start()

    }


    fun setupGraph(chart: BarChart, type: IOutputEnumCompanion) {
        var entries: List<BarEntry> = buildList {

            for(x in type.stringList().indices) {
                var entry = BarEntry(x.toFloat(), 0f)
                add(entry)
            }

        }

        data = BarDataSet(entries, "data")
        data.stackLabels = type.stringList().toTypedArray()


        chart.setFitBars(true)
        chart.data = BarData(data)
    }


    fun timeToString(time: Long): String{
        val hours = time / 3600
        val minutes = (time % 3600) / 60
        val seconds = time % 60

        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    fun updateLogbook() {
        var log = ""
        var values = ""

        // Activities
        fileManager.enum = Activity
        val actMap = fileManager.parse(date()).sum()
        for(a in Activity.values().drop(1)) {
            log += getString(R.string.logbook_entry, a.toString())
            values += getString(
                R.string.logbook_value,
                timeToString(actMap.get(a) ?: 0)
            )
        }
        log += "\n"
        values += "\n"
        // Activities
        fileManager.enum = Activity
        val resMap = fileManager.parse(date()).sum()
        for(r in Resp.values().drop(1)) {
            log += getString(R.string.logbook_entry, r.toString())
            values += getString(
                R.string.logbook_value,
                timeToString(resMap.get(r) ?: 0)
            )
        }

        logbook_names.setText(getString(R.string.logbook_title, log))
        logbook_values.setText(getString(R.string.logbook_value_header, values))
    }


    fun updateGraph(chart: BarChart, type: IOutputEnumCompanion) {
        fileManager.enum = type
        val actMap = fileManager.parse(date()).sum()
        var value: Long

        Log.d("graph", "Summed activities")

        // Build data set to use
        val labels = mutableListOf<String>()
        var index = 0
        val entries = buildList<BarEntry> {
            for(x in type.stringList().indices) {
                value = TimeUnit.SECONDS.toMinutes(actMap.get(Activity.find(x))?:0)

                if (Activity.find(x) == Activity.UNDEFINED ||
                    value == 0.toLong()
                )
                    continue
                Log.d("graph", "added bar " + Activity.find(x).toString())
                add(BarEntry(index.toFloat(), value.toFloat(), Activity.find(x).toString()))
                index ++
                labels.add(Activity.find(x).toString())
            }
        }

        if (entries.size == 0) {
            setupGraph(chart, type)
            return
        }
        data.values = entries
        data.stackLabels = labels.toTypedArray()

        Log.d("graph", "Set data")

        chart.setFitBars(true)
        chart.data = BarData(data)
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chart.invalidate()
    }


}

private fun List<ActivityEntry>.sum(): MutableMap<IOutputEnum, Long> {
    val actMap = mutableMapOf<IOutputEnum, Long>()
    var diff: Long

    if (this.size == 0) return actMap

    // Get sum of activities
    for (entry in this) {
        if (entry.end.before(entry.start))
            diff = TimeUnit.MILLISECONDS.toSeconds(entry.start.time - entry.end.time)
        else
            diff = TimeUnit.MILLISECONDS.toSeconds(entry.end.time - entry.start.time)
        Log.d("time", ""+ entry.start + " " + entry.end)
        actMap[entry.action] = (actMap.get(entry.action) ?: 0) + diff
    }
    return actMap
}

private fun DatePicker.getDate(): Date {
    val calendar = Calendar.getInstance()
    calendar.set(year, month, dayOfMonth)
    return calendar.time
}
