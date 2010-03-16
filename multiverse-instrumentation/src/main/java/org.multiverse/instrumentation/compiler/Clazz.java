package org.multiverse.instrumentation.compiler;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Peter Veentjer
 */
public class Clazz {

    private final String name;
    private byte[] bytecode;
    private ClassLoader classLoader;
    private Clazz original;
    private List<Clazz> createdList = new LinkedList<Clazz>();

    public Clazz(String name) {
        this.name = name;
    }

    public void setOriginal(Clazz original) {
        this.original = original;
    }

    public Clazz getOriginal() {
        return original;
    }

    public List<Clazz> getCreatedList() {
        return createdList;
    }

    public void setBytecode(byte[] bytecode) {
        this.bytecode = bytecode;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public String getName() {
        return name;
    }

    public byte[] getBytecode() {
        return bytecode;
    }
}
