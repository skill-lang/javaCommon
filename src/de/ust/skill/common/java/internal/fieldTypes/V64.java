package de.ust.skill.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.Collection;

import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

public final class V64 extends IntegerType<Long> {
    private final static V64 instance = new V64();

    public static V64 get() {
        return instance;
    }

    private V64() {
        super(11);
    }

    @Override
    public Long readSingleField(InStream in) {
        return in.v64();
    }

    @Override
    public long calculateOffset(Collection<Long> xs) {
        long result = 0L;
        for (long v : xs) {
            if (0L == (v & 0xFFFFFFFFFFFFFF80L)) {
                result += 1;
            } else if (0L == (v & 0xFFFFFFFFFFFFC000L)) {
                result += 2;
            } else if (0L == (v & 0xFFFFFFFFFFE00000L)) {
                result += 3;
            } else if (0L == (v & 0xFFFFFFFFF0000000L)) {
                result += 4;
            } else if (0L == (v & 0xFFFFFFF800000000L)) {
                result += 5;
            } else if (0L == (v & 0xFFFFFC0000000000L)) {
                result += 6;
            } else if (0L == (v & 0xFFFE000000000000L)) {
                result += 7;
            } else if (0L == (v & 0xFF00000000000000L)) {
                result += 8;
            } else {
                result += 9;
            }
        }
        return result;
    }

    @Override
    public long singleOffset(Long v) {
        if (0L == (v & 0xFFFFFFFFFFFFFF80L)) {
            return 1;
        } else if (0L == (v & 0xFFFFFFFFFFFFC000L)) {
            return 2;
        } else if (0L == (v & 0xFFFFFFFFFFE00000L)) {
            return 3;
        } else if (0L == (v & 0xFFFFFFFFF0000000L)) {
            return 4;
        } else if (0L == (v & 0xFFFFFFF800000000L)) {
            return 5;
        } else if (0L == (v & 0xFFFFFC0000000000L)) {
            return 6;
        } else if (0L == (v & 0xFFFE000000000000L)) {
            return 7;
        } else if (0L == (v & 0xFF00000000000000L)) {
            return 8;
        } else {
            return 9;
        }
    }

    @Override
    public void writeSingleField(Long target, OutStream out) throws IOException {
        out.v64(target);
    }

    @Override
    public String toString() {
        return "v64";
    }

    /**
     * helper method used by other offset calculations
     */
    public final static long singleV64Offset(long v) {
        if (0L == (v & 0xFFFFFFFFFFFFFF80L)) {
            return 1;
        } else if (0L == (v & 0xFFFFFFFFFFFFC000L)) {
            return 2;
        } else if (0L == (v & 0xFFFFFFFFFFE00000L)) {
            return 3;
        } else if (0L == (v & 0xFFFFFFFFF0000000L)) {
            return 4;
        } else if (0L == (v & 0xFFFFFFF800000000L)) {
            return 5;
        } else if (0L == (v & 0xFFFFFC0000000000L)) {
            return 6;
        } else if (0L == (v & 0xFFFE000000000000L)) {
            return 7;
        } else if (0L == (v & 0xFF00000000000000L)) {
            return 8;
        } else {
            return 9;
        }
    }
}
