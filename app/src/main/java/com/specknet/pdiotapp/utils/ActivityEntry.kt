package com.specknet.pdiotapp.utils

import com.github.mikephil.charting.data.BarEntry
import com.specknet.pdiotapp.test.Activity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class ActivityEntry constructor(var action: Activity, var start: Date,
                                var end: Date = Calendar.getInstance().time
) {


    override fun toString(): String {
        val formatter = SimpleDateFormat("HH:mm:ss")
        return "" + action + " from " + formatter.format(start) + " to " + formatter.format(end)
    }


}