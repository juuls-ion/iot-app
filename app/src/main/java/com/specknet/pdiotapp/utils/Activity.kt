package com.specknet.pdiotapp.utils

import com.specknet.pdiotapp.R

/**
 * Enum for managing physical activities
 * */
enum class Activity(override val value: Int, override val type: String = "physical_activity"):IOutputEnum {
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

    override fun toStringResource(): Int {
        return when(this){
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


    companion object: IOutputEnumCompanion {
        override fun find(value: Int?): Activity = Activity.values().find { it.value == value }?:Activity.UNDEFINED

        override fun fromOneHot(arr: FloatArray): Activity {
            // arg max function on arr
            val action = arr.withIndex().maxByOrNull { it.value }?.index
            return find(action)
        }

        override fun type(): String {
            return "physical_activity"
        }

        fun isDynamic(action: Activity): Boolean {
            return action in listOf(ASCENDING, DESCENDING, WALKING, RUNNING, SHUFFLE, MISC)
        }
    }

}

