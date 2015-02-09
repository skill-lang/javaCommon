package de.ust.skill.common.java.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import de.ust.skill.common.java.api.StringAccess;
import de.ust.skill.common.jvm.streams.FileInputStream;

/**
 * @author Timm Felden
 * @note String pools use magic index 0 for faster translation of string ids to
 *       strings.
 * @note String pool may contain duplicates, if strings have been added. This is
 *       a necessary behavior, if add should be an O(1) operation and Strings
 *       are loaded from file lazily.
 */
public class StringPool implements StringAccess {
    final private FileInputStream input;

    /**
     * the set of new strings, i.e. strings which do not have an ID
     */
    private final HashSet<String> newStrings = new HashSet<>();

    /**
     * ID ⇀ (absolute offset, length) will be used if idMap contains a null
     * reference
     *
     * @note there is a fake entry at ID 0
     */
    final ArrayList<Position> stringPositions;

    final static class Position {
        public Position(long l, int i) {
            absoluteOffset = l;
            length = i;
        }

        public long absoluteOffset;
        public int length;
    }

    /**
     * get string by ID
     */
    final ArrayList<String> idMap;

    StringPool(FileInputStream input) {
        this.input = input;
        stringPositions = new ArrayList<>();
        stringPositions.add(new Position(-1L, -1));
        idMap = new ArrayList<>();
        idMap.add(null);
    }

    @Override
    public int size() {
        return stringPositions.size() + newStrings.size();
    }

    @Override
    public boolean isEmpty() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean contains(Object o) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Iterator<String> iterator() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object[] toArray() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T> T[] toArray(T[] a) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean add(String e) {
        return newStrings.add(e);
    }

    @Override
    public boolean remove(Object o) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends String> c) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void clear() {
        // TODO Auto-generated method stub

    }

    @Override
    public String get(long index) {
        // TODO Auto-generated method stub
        return null;
    }

}
