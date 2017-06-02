package de.ust.skill.common.java.internal;

import java.util.Iterator;
import java.util.Set;

import de.ust.skill.common.java.internal.fieldDeclarations.AutoField;
import de.ust.skill.common.java.iterators.Iterators;

/**
 * Management of sub types is a bit different.
 * 
 * @author Timm Felden
 */
public class SubPool<T extends B, B extends SkillObject> extends StoragePool<T, B> {

    public SubPool(int poolIndex, String name, StoragePool<? super T, B> superPool, Set<String> knownFields,
            AutoField<?, T>[] autoFields) {
        super(poolIndex, name, superPool, knownFields, autoFields);
    }

    /**
     * Internal use only!
     */
    public Iterator<T> dataViewIterator(int begin, int end) {
        return Iterators.<T, B> fakeArray(basePool.data, begin, end);
    }
}
