package de.ust.skill.common.java.internal;

import java.nio.file.Path;
import java.util.Iterator;

import de.ust.skill.common.java.api.Access;
import de.ust.skill.common.java.api.SkillException;
import de.ust.skill.common.java.api.SkillFile;
import de.ust.skill.common.java.api.StringAccess;

public class SkillState implements SkillFile {

    private StringAccess strings;

    @Override
    public StringAccess Strings() {
        return strings;
    }

    @Override
    public Iterator<Access<? extends SkillObject>> allTypes() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void changePath(Path path) {
        // TODO Auto-generated method stub

    }

    @Override
    public void check() throws SkillException {
        // TODO Auto-generated method stub

    }

    @Override
    public void flush() throws SkillException {
        // TODO Auto-generated method stub

    }

    @Override
    public void close() throws SkillException {
        // TODO Auto-generated method stub

    }

}
