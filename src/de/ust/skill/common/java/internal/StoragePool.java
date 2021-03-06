package de.ust.skill.common.java.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import de.ust.skill.common.java.api.Access;
import de.ust.skill.common.java.api.SkillException;
import de.ust.skill.common.java.internal.fieldDeclarations.AutoField;
import de.ust.skill.common.java.internal.fieldTypes.Annotation;
import de.ust.skill.common.java.internal.fieldTypes.ReferenceType;
import de.ust.skill.common.java.internal.fieldTypes.V64;
import de.ust.skill.common.java.internal.parts.Block;
import de.ust.skill.common.java.internal.parts.BulkChunk;
import de.ust.skill.common.java.internal.parts.Chunk;
import de.ust.skill.common.java.internal.parts.SimpleChunk;
import de.ust.skill.common.jvm.streams.InStream;
import de.ust.skill.common.jvm.streams.OutStream;

/**
 * Top level implementation of all storage pools.
 * 
 * @author Timm Felden
 * @param <T>
 *            static type of instances
 * @param <B>
 *            base type of this hierarchy
 * @note Storage pools must be created in type order!
 * @note We do not guarantee functional correctness if instances from multiple
 *       skill files are mixed. Such usage will likely break at least one of the
 *       files.
 */
public class StoragePool<T extends B, B extends SkillObject> extends FieldType<T> implements Access<T>, ReferenceType {

    /**
     * Builder for new instances of the pool.
     * 
     * @author Timm Felden
     * @todo revisit implementation after the pool is completely implemented.
     *       Having an instance as constructor argument is questionable.
     */
    protected static abstract class Builder<T extends SkillObject> {
        protected StoragePool<T, ? super T> pool;
        protected T instance;

        protected Builder(StoragePool<T, ? super T> pool, T instance) {
            this.pool = pool;
            this.instance = instance;
        }

        /**
         * registers the object and invalidates the builder
         * 
         * @note abstract to work around JVM bug
         * @return the created object
         */
        abstract public T make();
    }

    final String name;

    // type hierarchy
    public final StoragePool<? super T, B> superPool;
    public final int typeHierarchyHeight;

    public final BasePool<B> basePool;

    StoragePool<?, B> nextPool;

    /**
     * solves type equation
     */
    @SuppressWarnings("unchecked")
    private void setNextPool(StoragePool<?, ?> nx) {
        nextPool = (StoragePool<?, B>) nx;
    }

    /**
     * @return next pool of this hierarchy in weak type order
     */
    public StoragePool<?, B> nextPool() {
        return nextPool;
    }

    /**
     * initialize the next pointer
     * 
     * @note invoked from base pool
     * @note destroys subPools, because they are no longer needed
     */
    static void establishNextPools(ArrayList<StoragePool<?, ?>> types) {
        StoragePool<?, ?>[] L = new StoragePool<?, ?>[types.size()];

        // walk in reverse and store last reference in L[base]
        for (int i = types.size() - 1; i >= 0; i--) {
            final StoragePool<?, ?> t = types.get(i);

            // skip base pools, because their next link has been established by
            // their sub pools already
            final StoragePool<?, ?> p = t.superPool;
            if (null == p)
                continue;

            // ensure that every pool has a last pointer
            final int id = t.typeID - 32;
            if (null == L[id])
                L[id] = t;

            // insert into parent link
            if (null == p.nextPool) {
                L[p.typeID - 32] = L[id];
            } else {
                L[id].setNextPool(p.nextPool);
            }
            p.setNextPool(t);
        }
    }

    /**
     * pointer to base-pool-managed data array
     */
    protected B[] data;

    /**
     * names of known fields, the actual field information is given in the
     * generated addKnownFiled method.
     */
    public final String[] knownFields;
    public static final String[] noKnownFields = new String[0];

    /**
     * all fields that are declared as auto, including skillID
     * 
     * @note stores fields at index "-f.index"
     * @note sub-constructor adds auto fields from super types to this array;
     *       this is an optimization to make iteration O(1); the array cannot
     *       change anyway
     * @note the initial type constructor will already allocate an array of the
     *       correct size, because the right size is statically known (a
     *       generation time constant)
     */
    protected final AutoField<?, T>[] autoFields;
    /**
     * used as placeholder, if there are no auto fields at all to optimize
     * allocation time and memory usage
     */
    static final AutoField<?, ?>[] noAutoFields = new AutoField<?, ?>[0];

    /**
     * @return magic cast to placeholder which well never fail at runtime,
     *         because the array is empty anyway
     */
    @SuppressWarnings("unchecked")
    protected static final <T extends SkillObject> AutoField<?, T>[] noAutoFields() {
        return (AutoField<?, T>[]) noAutoFields;
    }

    /**
     * all fields that hold actual data
     * 
     * @note stores fields at index "f.index-1"
     */
    protected final ArrayList<FieldDeclaration<?, T>> dataFields;

    @Override
    public StaticFieldIterator fields() {
        return new StaticFieldIterator(this);
    }

    @Override
    public FieldIterator allFields() {
        return new FieldIterator(this);
    }

    /**
     * The block layout of instances of this pool.
     */
    final ArrayList<Block> blocks = new ArrayList<>();

    /**
     * internal use only!
     */
    public ArrayList<Block> blocks() {
        return blocks;
    }

    /**
     * internal use only!
     */
    public Block lastBlock() {
        return blocks.get(blocks.size() - 1);
    }

    /**
     * All stored objects, which have exactly the type T. Objects are stored as
     * arrays of field entries. The types of the respective fields can be
     * retrieved using the fieldTypes map.
     */
    final ArrayList<T> newObjects = new ArrayList<>();

    /**
     * retrieve a new object
     * 
     * @param index
     *            in [0;{@link #newObjectsSize()}[
     * @return the new object at the given position
     */
    public final T newObject(int index) {
        return newObjects.get(index);
    }

    /**
     * Ensures that at least capacity many new objects can be stored in this
     * pool without moving references.
     */
    public final void hintNewObjectsSize(int capacity) {
        newObjects.ensureCapacity(capacity);
    }

    protected final DynamicNewInstancesIterator<T, B> newDynamicInstances() {
        return new DynamicNewInstancesIterator<>(this);
    }

    protected final int newDynamicInstancesSize() {
        int rval = 0;
        TypeHierarchyIterator<T, B> ts = new TypeHierarchyIterator<>(this);
        while (ts.hasNext())
            rval += ts.next().newObjects.size();

        return rval;
    }

    /**
     * Number of static instances of T in data. Managed by read/compress.
     */
    int staticDataInstances;

    /**
     * the number of instances of exactly this type, excluding sub-types
     * 
     * @return size excluding subtypes
     */
    final public int staticSize() {
        return staticDataInstances + newObjects.size();
    }

    /***
     * @note cast required to work around weakened type system by javac 1.8.131
     */
    @Override
    @SuppressWarnings("unchecked")
    public final StaticDataIterator<T> staticInstances() {
        return (StaticDataIterator<T>) new StaticDataIterator<SkillObject>(
                (StoragePool<SkillObject, SkillObject>) this);
    }

    /**
     * storage pools can be fixed, i.e. no dynamic instances can be added to the
     * pool. Fixing a pool requires that it does not contain a new object.
     * Fixing a pool will fix subpools as well. Un-fixing a pool will un-fix
     * super pools as well, thus being fixed is a transitive property over the
     * sub pool relation. Pools will be fixed by flush operations.
     */
    boolean fixed = false;
    /**
     * size that is only valid in fixed state
     */
    int cachedSize;

    /**
     * number of deleted objects in this state
     */
    protected int deletedCount = 0;

    /**
     * !!internal use only!!
     */
    public final boolean fixed() {
        return fixed;
    }

    /**
     * fix all pool sizes
     * 
     * @note this may change the result of size(), because from now on, the
     *       deleted objects will be taken into account
     */
    static final void fixed(ArrayList<StoragePool<?, ?>> pools) {
        // set cached size to static size
        for (StoragePool<?, ?> p : pools) {

            // take deletions into account
            p.cachedSize = p.staticSize() - p.deletedCount;
            p.fixed = true;
        }

        // bubble up cached sizes to parents
        for (int i = pools.size() - 1; i >= 0; i--) {
            StoragePool<?, ?> p = pools.get(i);
            if (null != p.superPool)
                p.superPool.cachedSize += p.cachedSize;
        }
    }

    /**
     * unset fixed status
     */
    static final void unfix(ArrayList<StoragePool<?, ?>> pools) {
        for (StoragePool<?, ?> p : pools)
            p.fixed = false;
    }

    @Override
    final public String name() {
        return name;
    }

    @Override
    final public String superName() {
        if (null != superPool)
            return superPool.name;
        return null;
    }

    /**
     * @note the unchecked cast is required, because we can not supply this as
     *       an argument in a super constructor, thus the base pool can not be
     *       an argument to the constructor. The cast will never fail anyway.
     */
    @SuppressWarnings("unchecked")
    protected StoragePool(int poolIndex, String name, StoragePool<? super T, B> superPool, String[] knownFields,
            AutoField<?, T>[] autoFields) {
        super(32 + poolIndex);
        this.name = name.intern();

        this.superPool = superPool;
        if (null == superPool) {
            this.typeHierarchyHeight = 0;
            this.basePool = (BasePool<B>) this;
        } else {
            this.typeHierarchyHeight = superPool.typeHierarchyHeight + 1;
            this.basePool = superPool.basePool;
        }
        this.knownFields = knownFields;
        dataFields = new ArrayList<>(knownFields.length);
        this.autoFields = autoFields;
    }

    /**
     * @return the instance matching argument skill id
     */
    @SuppressWarnings("unchecked")
    final public T getByID(int ID) {
        int index = ID - 1;
        if (index < 0 | data.length <= index)
            return null;
        return (T) data[index];
    }

    @SuppressWarnings("unchecked")
    @Override
    public final T readSingleField(InStream in) {
        int index = in.v32() - 1;
        if (index < 0 | data.length <= index)
            return null;
        return (T) data[index];
    }

    @Override
    public final long calculateOffset(Collection<T> xs) {
        // shortcut small compressed types
        if (data.length < 128)
            return xs.size();

        long result = 0L;
        for (T x : xs) {
            result += null == x ? 1 : V64.singleV64Offset(x.skillID);
        }
        return result;
    }

    @Override
    public final long singleOffset(T x) {
        if (null == x)
            return 1L;

        int v = x.skillID;
        if (0 == (v & 0xFFFFFF80)) {
            return 1;
        } else if (0 == (v & 0xFFFFC000)) {
            return 2;
        } else if (0 == (v & 0xFFE00000)) {
            return 3;
        } else if (0 == (v & 0xF0000000)) {
            return 4;
        } else {
            return 5;
        }
    }

    @Override
    public final void writeSingleField(T ref, OutStream out) throws IOException {
        if (null == ref)
            out.i8((byte) 0);
        else
            out.v64(ref.skillID);
    }

    /**
     * @return size including subtypes
     */
    @Override
    final public int size() {
        if (fixed)
            return cachedSize;

        int size = 0;
        TypeHierarchyIterator<T, B> ts = new TypeHierarchyIterator<>(this);
        while (ts.hasNext())
            size += ts.next().staticSize();
        return size;
    }

    @Override
    final public Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    public T[] toArray(T[] a) {
        final T[] rval = Arrays.copyOf(a, size());
        DynamicDataIterator<T, B> is = iterator();
        for (int i = 0; i < rval.length; i++) {
            rval[i] = is.next();
        }
        return rval;
    }

    /**
     * Add an existing instance as a new objects.
     * 
     * @note Do not use objects managed by other skill files.
     */
    public final boolean add(T e) {
        if (fixed)
            throw new IllegalStateException("can not fix a pool that contains new objects");

        return newObjects.add(e);
    }

    /**
     * Delete shall only be called from skill state
     * 
     * @param target
     *            the object to be deleted
     * @note we type target using the erasure directly, because the Java type
     *       system is too weak to express correct typing, when taking the pool
     *       from a map
     */
    final void delete(SkillObject target) {
        if (!target.isDeleted()) {
            target.skillID = 0;
            deletedCount++;
        }
    }

    @Override
    public SkillState owner() {
        return basePool.owner();
    }

    @Override
    final public DynamicDataIterator<T, B> iterator() {
        return new DynamicDataIterator<T, B>(this);
    }

    @Override
    final public TypeOrderIterator<T, B> typeOrderIterator() {
        return new TypeOrderIterator<T, B>(this);
    }

    @Override
    public T make() throws SkillException {
        throw new SkillException("We prevent reflective creation of new instances, because it is bad style!");
    }

    /**
     * insert new T instances with default values based on the last block info
     * 
     * @note defaults to unknown objects to reduce code size
     */
    @SuppressWarnings("unchecked")
    protected void allocateInstances(Block last) {
        int i = last.bpo;
        final int high = i + last.staticCount;
        while (i < high) {
            data[i] = (T) (new SkillObject.SubType(this, i + 1));
            i += 1;
        }
    }

    protected final void updateAfterCompress(int[] lbpoMap) {
        // update data
        data = basePool.data;

        // update structural knowledge of data
        staticDataInstances += newObjects.size() - deletedCount;
        deletedCount = 0;
        newObjects.clear();
        newObjects.trimToSize();

        blocks.clear();
        blocks.add(new Block(lbpoMap[typeID - 32], cachedSize, staticDataInstances));
    }

    /**
     * internal use only! adds an unknown field
     */
    public <R> FieldDeclaration<R, T> addField(FieldType<R> type, String name) {
        return new LazyField<R, T>(type, name, this);
    }

    /**
     * used internally for state allocation
     */
    @SuppressWarnings("static-method")
    public void addKnownField(String name, StringPool string, Annotation annotation) {
        throw new Error("Arbitrary storage pools know no fields!");
    }

    /**
     * used internally for type forest construction
     */
    public StoragePool<? extends T, B> makeSubPool(int index, String name) {
        return new StoragePool<>(index, name, this, noKnownFields, noAutoFields());
    }

    /**
     * called after a prepare append operation to write empty the new objects
     * buffer and to set blocks correctly
     */
    protected final void updateAfterPrepareAppend(int lbpoMap[], HashMap<FieldDeclaration<?, ?>, Chunk> chunkMap) {
        // update data as it may have changed
        this.data = basePool.data;

        final boolean newInstances = newDynamicInstances().hasNext();
        final boolean newPool = blocks.isEmpty();
        final boolean newField;
        {
            boolean exists = false;
            for (FieldDeclaration<?, T> f : dataFields) {
                if (0 == f.dataChunks.size()) {
                    exists = true;
                    break;
                }
            }

            newField = exists;
        }

        if (newPool || newInstances || newField) {

            // build block chunk
            final int lcount = newDynamicInstancesSize();
            // //@ note this is the index into the data array and NOT the
            // written lbpo
            final int lbpo = (0 == lcount) ? 0 : lbpoMap[typeID - 32];

            blocks.add(new Block(lbpo, lcount, newObjects.size()));
            final int blockCount = blocks.size();
            staticDataInstances += newObjects.size();

            // @note: if this does not hold for p; then it will not hold for
            // p.subPools either!
            if (newInstances || !newPool) {
                // build field chunks
                for (FieldDeclaration<?, T> f : dataFields) {
                    if (0 == f.index)
                        continue;

                    final Chunk c;
                    if (0 == f.dataChunks.size() && 1 != blockCount) {
                        c = new BulkChunk(-1, -1, cachedSize, blockCount);
                    } else if (newInstances) {
                        c = new SimpleChunk(-1, -1, lbpo, lcount);
                    } else
                        continue;

                    f.addChunk(c);
                    synchronized (chunkMap) {
                        chunkMap.put(f, c);
                    }
                }
            }
        }

        // remove new objects, because they are regular objects by now
        newObjects.clear();
        newObjects.trimToSize();
    }

    @Override
    final public String toString() {
        return name;
    }
}
