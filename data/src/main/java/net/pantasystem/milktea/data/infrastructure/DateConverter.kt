package net.pantasystem.milktea.data.infrastructure

import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.datetime.Instant
import java.text.SimpleDateFormat
import java.util.*

@TypeConverters
class DateConverter{

    companion object{
        private val smf = SimpleDateFormat.getInstance()
    }

    @TypeConverter
    fun toDate(formattedDate: String): Date{
        return smf.parse(formattedDate)!!
    }

    @TypeConverter
    fun fromDate(date: Date): String{
        return smf.format(date)
    }
}

@TypeConverters
object InstantConverter {

    @TypeConverter
    fun toInstant(iso8601DateTime: String): Instant {
        return Instant.parse(iso8601DateTime)
    }

    @TypeConverter
    fun fromInstant(instant: Instant): String{
        return instant.toString()
    }
}