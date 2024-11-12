package com.specknet.pdiotapp.utils

import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Model(val model: Interpreter, val inpDim: Int) {

    fun streamToInput(floatArray: List<FloatArray>): ByteBuffer {
        // Calculate the total size needed for the ByteBuffer
        val byteBuffer = ByteBuffer.allocateDirect(floatArray.size * inpDim * 4) // 4 bytes for each float
        byteBuffer.order(ByteOrder.nativeOrder()) // Set the byte order to native

        // Fill the ByteBuffer with data
        for (i in floatArray.indices) {
            for (j in floatArray[i].indices.reversed().drop(6-inpDim)) {
                byteBuffer.putFloat(floatArray[i][j])
            }
        }
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
        val inF = streamToInput(input.dropLast(1))

        Log.d("classify", "classify: Running model")

        model.run(inF, out)
        return out
    }

}