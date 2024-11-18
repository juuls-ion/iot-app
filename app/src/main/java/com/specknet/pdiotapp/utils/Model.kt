package com.specknet.pdiotapp.utils

import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.pow
import kotlin.math.sqrt

class Model(val model: Interpreter, val inpDimWidth: Int, val inpDimHeight: Int, val means: List<Float>, val stds: List<Float>) {

    // Stream input is res_x res_y res_z thing_x thing_y thing_z
    fun <T>List<List<T>>.transpose(): List<List<T>> {
        return (this[0].indices).map { i -> (this.indices).map { j -> this[j][i] } }
    }

    fun List<Float>.std(): Double {
        val mean = this.average()
        val variance = this.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }

    fun appendFeatures(floatArray: Array<FloatArray>, buffer: ByteBuffer): ByteBuffer {
        // we want mean,stc,min,max of each column of the float array.
        val transpose = floatArray.map { it.asList() }.toList().transpose()
        var dim: List<Float>

        var feature = buildList {
            for (i in (0 until inpDimWidth)) {
                dim = transpose[i]
                add(dim.average())
                add(dim.std())
                add(dim.minOf{ it })
                add(dim.maxOf{ it })
            }

            dim = floatArray.map {it -> sqrt((it[0].pow(2) + it[1].pow(2) + it[2].pow(2)).toDouble()).toFloat() }
            add(dim.average())
            add(dim.std())
            add(dim.minOf{ it })
            add(dim.maxOf{ it })
        }

        for (feat in feature) buffer.putFloat(feat.toFloat())

        return buffer
    }


    fun norm(floatArray: Array<FloatArray>): Array<FloatArray> {
        for (i in 0 until inpDimHeight) {
            for (j in 0 until inpDimWidth) {
                floatArray[i][j] = (floatArray[i][j] - means[j]) / stds[j]
            }
        }

        return floatArray

    }


    fun streamToInput(floatArray: Array<FloatArray>): ByteBuffer {
        // Calculate the total size needed for the ByteBuffer
        val floatArray = norm(floatArray)
        val byteBuffer = ByteBuffer.allocateDirect(inpDimHeight * inpDimWidth * 4 + inpDimWidth*4*4 + 16) // 4 bytes for each float
        byteBuffer.order(ByteOrder.nativeOrder()) // Set the byte order to native
        // Fill the ByteBuffer with data
        for (i in 0 until inpDimHeight)
            for (j in 0 until inpDimWidth)
                byteBuffer.putFloat(floatArray[i+((floatArray.size-1)-inpDimHeight)][j])


        appendFeatures(floatArray, byteBuffer)

        byteBuffer.rewind() // Reset the position to the beginning
        return byteBuffer
    }

    fun classify(input: Array<FloatArray>, out: FloatBuffer) : FloatBuffer{
        /**
         * Runs a model on some given data, pasting it to the output array.
         * @param input Input data of format Array<FloatArray>, the last element of the array will be culled.
         * @param out Output buffer of format FloatBuffer
         * @param model Interpreter class version of a tensorflow model
         * @param type Integer indicating which model is used, 0 -> physical, 1 -> respiratory
         * @return the output buffer
         */

        // Get rid of buffer using dropLast
        Log.d("classify", "classify: Attempting input cleaning")
        val inF = streamToInput(input)

        Log.d("classify", "classify: Running model")

        model.run(inF, out)
        return out
    }

}