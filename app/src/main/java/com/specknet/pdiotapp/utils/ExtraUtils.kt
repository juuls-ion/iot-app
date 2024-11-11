package com.specknet.pdiotapp.utils

import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class ExtraUtils {


    companion object {

        fun <T> moveLeft(arr: Array<T>, fill: T): Array<T>{
            for (i in arr.indices.reversed().drop(1))
                arr[i] = arr[i+1]
            arr[arr.size - 1] = fill

            return arr
        }


        fun convertToPhysicalInput(floatArray: List<FloatArray>): ByteBuffer {
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

        fun convertToRespInput(floatArray: List<FloatArray>): ByteBuffer {
            // Calculate the total size needed for the ByteBuffer
            val byteBuffer = ByteBuffer.allocateDirect(floatArray.size * floatArray[0].size * 4) // 4 bytes for each float
            byteBuffer.order(ByteOrder.nativeOrder()) // Set the byte order to native

            // Fill the ByteBuffer with data
            for (i in floatArray.indices) {
                for (j in floatArray[i].indices.drop(3)) {
                    byteBuffer.putFloat(floatArray[i][j])
                }
            }
            byteBuffer.rewind() // Reset the position to the beginning
            return byteBuffer
        }

        fun classify(input: Array<FloatArray>, out: FloatBuffer, model: Interpreter, type: Int) : FloatBuffer{
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
            val inF = when(type) {
                0 -> convertToPhysicalInput(input.dropLast(1))
                1 -> convertToRespInput(input.dropLast(1))
                else -> {return out}
            }

            Log.d("classify", "classify: Running model")

            model.run(inF, out)
            return out
        }
    }

}