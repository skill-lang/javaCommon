package de.ust.skill.common.java.api;

import de.ust.skill.common.java.internal.SkillObject;

/**
 * An abstract Field declaration, used for the runtime representation of types.
 * It can be used for reflective access of types.
 * 
 * @author Timm Felden
 * @param <T>
 *            runtime type of the field modulo boxing
 */
public abstract class FieldDeclaration<T> {
    /**
     * @return the skill type of this field
     */
    public abstract FieldType<T> type();

    /**
     * @return skill name of this field
     */
    public abstract String name();

    /**
     * @return enclosing type
     */
    public abstract GeneralAccess<?> owner();

    /**
     * Generic getter for an object.
     */
    public abstract T get(SkillObject ref);

    /**
     * Generic setter for an object.
     */
    public abstract void set(SkillObject ref, T value);
}
