package de.ust.skill.common.java.internal.fieldTypes;

import java.io.IOException;
import java.util.Collection;

import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

public final class F64 extends FloatType<Double> {

    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 13;

    private final static F64 instance = new F64();

    public static F64 get() {
        return instance;
    }

    private F64() {
        super(typeID);
    }

    @Override
    public Double readSingleField(InStream in) {
        return in.f64();
    }

    @Override
    public long calculateOffset(Collection<Double> xs) {
        return 8 * xs.size();
    }

    @Override
    public long singleOffset(Double x) {
        return 8L;
    }

    @Override
    public void writeSingleField(Double target, OutStream out) throws IOException {
        out.f64(null == target ? 0 : target);
    }

    @Override
    public String toString() {
        return "f64";
    }
}
