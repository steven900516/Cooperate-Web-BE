package com.conghuhu.utils;

import java.time.*;

public class TimeUtils {


    public static int timeMinus(LocalDateTime startTime,LocalDateTime endTime){
        Duration duration = Duration.between(startTime, endTime);
        long l = duration.toMillis();
        return Integer.parseInt(Long.toString(l));
    }



    public static LocalDateTime UTCToNORMAL(LocalDateTime localDateTime) {
        ZonedDateTime zonedtime = localDateTime.atZone(ZoneId.from(ZoneOffset.UTC));
        ZonedDateTime converted = zonedtime.withZoneSameInstant(ZoneOffset.ofHours(8));
        return converted.toLocalDateTime();

    }
}
