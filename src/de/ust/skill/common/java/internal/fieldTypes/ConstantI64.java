package de.ust.skill.common.java.internal.fieldTypes;

public final class ConstantI64 extends ConstantIntegerType<Long> {
    
    /**
     * @see SKilL V1.0 reference manual §G
     */
    public static final int typeID = 3;
    
    public final long value;

    public ConstantI64(long value) {
        super(typeID);
        this.value = value;
    }

    @Override
    public Long value() {
        return value;
    }

    @Override
    public String toString() {
        return String.format("const i64 = %016X", value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ConstantI64)
            return value == ((ConstantI64) obj).value;
        return false;
    }
}
