package com.specknet.pdiotapp.utils

import android.util.Log
import com.specknet.pdiotapp.test.Activity
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class ExtraUtils {


    companion object {

        fun moveLeft(arr: Array<FloatArray>, inputSize: Int): Array<FloatArray>{
            for (i in arr.indices.reversed().drop(1))
                arr[i] = arr[i+1]
            arr[arr.size - 1] = FloatArray(inputSize)

            return arr
        }

        fun fromOneHot(arr: FloatArray): Activity? {
            // arg max function on arr
            val action = arr.withIndex().maxByOrNull { it.value }?.index
            return Activity.find(action)
        }

        fun floatArrayToBuffer(floatArray: FloatArray): FloatBuffer? {
            val byteBuffer: ByteBuffer = ByteBuffer
                .allocateDirect(floatArray.size * 4)

            byteBuffer.order(ByteOrder.nativeOrder())

            val floatBuffer: FloatBuffer = byteBuffer.asFloatBuffer()

            floatBuffer.put(floatArray)
            floatBuffer.position(0)
            return floatBuffer
        }

        fun floatArray2DToByteBuffer(floatArray: List<FloatArray>): ByteBuffer {
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

        fun classify(input: Array<FloatArray>, out: FloatBuffer, model: Interpreter) : FloatBuffer?{
            /**
             * Runs a model on some given data, pasting it to the output array.
             * @param input Input data of format Array<FloatArray>, the last element of the array will be culled.
             * @param out Output buffer of format FloatBuffer
             * @param model Interpreter class version of a tensorflow model
             * @return the output buffer
             */

            // Get rid of buffer using dropLast
            Log.d("classify", "classify: Attempting input cleaning")

            val inF = floatArray2DToByteBuffer(input.dropLast(1))

            Log.d("classify", "classify: Running model")

            model.run(inF, out)
            return out
        }
    }

}