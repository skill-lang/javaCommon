package de.ust.skill.common.java.internal.FieldTypes;

import java.util.LinkedList;

import de.ust.skill.common.java.internal.FieldType;
import de.ust.skill.common.jvm.streams.InStream;

public final class ListType<T> extends IntegerType<LinkedList<T>> {
    public final FieldType<T> groundType;

    public ListType(FieldType<T> groundType) {
        super(18);
        this.groundType = groundType;
    }

    @Override
    public LinkedList<T> readSingleField(InStream in) {
        LinkedList<T> rval = new LinkedList<>();
        for (int i = (int) in.v64(); i != 0; i--)
            rval.add(groundType.readSingleField(in));
        return rval;
    }

    @Override
    public String toString() {
        return "list<" + groundType.toString() + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ListType<?>)
            return groundType.equals(((ListType<?>) obj).groundType);
        return false;
    }
}
