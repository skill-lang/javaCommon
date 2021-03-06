package de.ust.skill.common.java.internal;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.Semaphore;

import de.ust.skill.common.java.api.SkillException;
import de.ust.skill.common.java.internal.exceptions.PoolSizeMissmatchError;
import de.ust.skill.common.java.internal.fieldDeclarations.AutoField;
import de.ust.skill.common.java.internal.fieldDeclarations.IgnoredField;
import de.ust.skill.common.java.internal.parts.Block;
import de.ust.skill.common.java.internal.parts.BulkChunk;
import de.ust.skill.common.java.internal.parts.Chunk;
import de.ust.skill.common.java.internal.parts.SimpleChunk;
import de.ust.skill.common.java.restrictions.FieldRestriction;
import de.ust.skill.common.jvm.streams.FileInputStream;
import de.ust.skill.common.jvm.streams.MappedInStream;
import de.ust.skill.common.jvm.streams.MappedOutStream;

/**
 * Actual implementation as used by all bindings.
 * 
 * @author Timm Felden
 */
abstract public class FieldDeclaration<T, Obj extends SkillObject>
        extends de.ust.skill.common.java.api.FieldDeclaration<T> {

    /**
     * @note types may change during file parsing. this may seem like a hack, but it makes file parser implementation a
     *       lot easier, because there is no need for two mostly similar type hierarchy implementations
     */
    protected FieldType<T> type;

    @Override
    public final FieldType<T> type() {
        return type;
    }

    /**
     * skill name of this
     */
    final String name;

    @Override
    public String name() {
        return name;
    }

    /**
     * index as used in the file
     * 
     * @note index is > 0, if the field is an actual data field
     * @note index = 0, if the field is SKilLID (if supported by generator; deprecated)
     * @note index is <= 0, if the field is an auto field (or SKilLID)
     * @note fieldIDs should be file-global, so that remaining HashMaps with field keys can be replaced
     */
    final int index;

    /**
     * the enclosing storage pool
     */
    protected final StoragePool<Obj, ? super Obj> owner;

    /**
     * Restriction handling.
     */
    public final HashSet<FieldRestriction<T>> restrictions = new HashSet<>();

    @SuppressWarnings("unchecked")
    public <U> void addRestriction(FieldRestriction<U> r) {
        restrictions.add((FieldRestriction<T>) r);
    }

    /**
     * Check consistency of restrictions on this field.
     */
    void check() {
        if (!restrictions.isEmpty())
            for (Obj x : owner)
                if (!x.isDeleted())
                    for (FieldRestriction<T> r : restrictions)
                        r.check(get(x));
    }

    @Override
    public StoragePool<Obj, ? super Obj> owner() {
        return owner;
    }

    /**
     * regular field constructor
     */
    protected FieldDeclaration(FieldType<T> type, String name, StoragePool<Obj, ? super Obj> owner) {
        this.type = type;
        this.name = name.intern(); // we will switch on names, thus we need to
                                   // intern them
        this.owner = owner;
        owner.dataFields.add(this);
        this.index = owner.dataFields.size();
    }

    /**
     * auto field constructor
     */
    protected FieldDeclaration(FieldType<T> type, String name, int index, StoragePool<Obj, ? super Obj> owner) {
        this.type = type;
        this.name = name.intern(); // we will switch on names, thus we need to
                                   // intern them
        this.owner = owner;
        this.index = index;
        owner.autoFields[-index] = (AutoField<?, Obj>) this;
    }

    @Override
    public String toString() {
        return type.toString() + " " + name;
    }

    /**
     * Field declarations are equal, iff their names and types are equal.
     * 
     * @note This makes fields of unequal enclosing types equal!
     */
    @Override
    public final boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof FieldDeclaration) {
            return ((FieldDeclaration<?, ?>) obj).name().equals(name)
                    && ((FieldDeclaration<?, ?>) obj).type.equals(type);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return type.hashCode() ^ name.hashCode();
    }

    protected final ArrayList<Chunk> dataChunks = new ArrayList<>();

    public final void addChunk(Chunk chunk) {
        dataChunks.add(chunk);
    }

    /**
     * Make offsets absolute.
     * 
     * @return the end of this chunk
     */
    final long addOffsetToLastChunk(FileInputStream in, long offset) {
        final Chunk c = lastChunk();
        c.begin += offset;
        c.end += offset;

        return c.end;
    }

    protected final Chunk lastChunk() {
        return dataChunks.get(dataChunks.size() - 1);
    }

    /**
     * reset Chunks before writing a file
     */
    final void resetChunks(int lbpo, int newSize) {
        dataChunks.clear();
        dataChunks.add(new SimpleChunk(-1, -1, lbpo, newSize));
    }

    /**
     * Read data from a mapped input stream and set it accordingly. This is invoked at the very end of state
     * construction and done massively in parallel.
     */
    protected abstract void rsc(int i, final int end, MappedInStream in);

    /**
     * Read data from a mapped input stream and set it accordingly. This is invoked at the very end of state
     * construction and done massively in parallel.
     */
    protected abstract void rbc(BulkChunk target, MappedInStream in);

    /**
     * offset cache; calculated by osc/obc; reset is done by the caller (simplifies obc)
     */
    protected long offset;

    /**
     * offset calculation as preparation of writing data belonging to the owners last block
     */
    protected abstract void osc(int i, final int end);

    /**
     * offset calculation as preparation of writing data belonging to the owners last block
     * 
     * @note Defer reading to osc by creating adequate temporary simple chunks.
     */
    protected final void obc(BulkChunk c) {
        ArrayList<Block> blocks = owner.blocks();
        int blockIndex = 0;
        final int endBlock = c.blockCount;
        while (blockIndex < endBlock) {
            Block b = blocks.get(blockIndex++);
            int i = b.bpo;
            osc(i, i + b.count);
        }
    }

    /**
     * write data into a map at the end of a write/append operation
     * 
     * @note this will always write the last chunk, as, in contrast to read, it is impossible to write to fields in
     *       parallel
     * @note only called, if there actually is field data to be written
     */
    protected abstract void wsc(int i, final int end, MappedOutStream out) throws IOException;

    /**
     * write data into a map at the end of a write/append operation
     * 
     * @note this will always write the last chunk, as, in contrast to read, it is impossible to write to fields in
     *       parallel
     * @note only called, if there actually is field data to be written
     * @note Defer reading to wsc by creating adequate temporary simple chunks.
     */
    protected final void wbc(BulkChunk c, MappedOutStream out) throws IOException {
        ArrayList<Block> blocks = owner.blocks();
        int blockIndex = 0;
        final int endBlock = c.blockCount;
        while (blockIndex < endBlock) {
            Block b = blocks.get(blockIndex++);
            int i = b.bpo;
            wsc(i, i + b.count, out);
        }
    }

    /**
     * Coordinates reads and prevents from state corruption using the barrier.
     * 
     * @param barrier
     *            takes one permit in the caller thread and returns one in the reader thread (per block)
     * @param readErrors
     *            errors will be reported in this queue
     * @return number of jobs started
     */
    final int finish(final Semaphore barrier, final ArrayList<SkillException> readErrors, final FileInputStream in) {
        // skip lazy and ignored fields
        if (this instanceof IgnoredField)
            return 0;

        int block = 0;
        for (final Chunk c : dataChunks) {
            final int blockCounter = block++;
            final FieldDeclaration<T, Obj> f = this;

            SkillState.pool.execute(new Runnable() {
                @Override
                public void run() {
                    SkillException ex = null;
                    try {
                        // check that map was fully consumed and remove it
                        MappedInStream map = in.map(0L, c.begin, c.end);
                        if (c instanceof BulkChunk)
                            f.rbc((BulkChunk) c, map);
                        else {
                            int i = (int) ((SimpleChunk) c).bpo;
                            f.rsc(i, i + (int) c.count, map);
                        }

                        if (!map.eof() && !(f instanceof LazyField<?, ?>))
                            ex = new PoolSizeMissmatchError(blockCounter, map.position(), c.begin, c.end, f);

                    } catch (BufferUnderflowException e) {
                        ex = new PoolSizeMissmatchError(blockCounter, c.begin, c.end, f, e);
                    } catch (SkillException t) {
                        ex = t;
                    } catch (Throwable t) {
                        ex = new SkillException("internal error: unexpected foreign exception", t);
                    } finally {
                        barrier.release();
                        if (null != ex)
                            synchronized (readErrors) {
                                readErrors.add(ex);
                            }
                    }
                }
            });
        }
        return block;
    }

    /**
     * punch a hole into the java type system :)
     */
    @SuppressWarnings("unchecked")
    protected static final <T, U> FieldType<T> cast(FieldType<U> f) {
        return (FieldType<T>) f;
    }

    /**
     * punch a hole into the java type system that eases implementation of maps of interfaces
     * 
     * @note the hole is only necessary, because interfaces cannot inherit from classes
     */
    @SuppressWarnings("unchecked")
    protected static final <K1, V1, K2, V2> HashMap<K1, V1> castMap(HashMap<K2, V2> arg) {
        return (HashMap<K1, V1>) arg;
    }
}
