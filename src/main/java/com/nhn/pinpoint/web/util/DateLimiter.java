package com.nhn.pinpoint.web.util;

import com.nhn.pinpoint.web.vo.Range;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @author emeroad
 */
@Component
public class DateLimiter implements Limiter {

    private final long limitDay;
    private final long limitDayMillis;

    public DateLimiter() {
        this(2);
    }

    public DateLimiter(int limitDay) {
        if (limitDay < 0) {
            throw new IllegalArgumentException("limitDay < 0 " + limitDay);
        }
        this.limitDay = limitDay;
        this.limitDayMillis = TimeUnit.DAYS.toMillis((long) limitDay);
    }

    @Override
    public void limit(long from, long to) {
        final long elapsedTime = to - from;
        if (elapsedTime < 0) {
            throw new  IllegalArgumentException("to - from < 0 from:" + from + " to:" + to);
        }
        if (limitDayMillis < elapsedTime) {
            throw new IllegalArgumentException("limitDay:"+ limitDay + " from:" + from + " to:" + to);
        }
    }

    @Override
    public void limit(Range range) {
        if (range == null) {
            throw new NullPointerException("range must not be null");
        }
        final long elapsedTime = range.getRange();
        if (elapsedTime < 0) {
            throw new  IllegalArgumentException("to - from < 0 " + range);
        }
        if (limitDayMillis < elapsedTime) {
            throw new IllegalArgumentException("limitDay:"+ limitDay + " " + range);
        }
    }
}
