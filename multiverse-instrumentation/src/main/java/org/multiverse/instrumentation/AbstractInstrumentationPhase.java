package org.multiverse.instrumentation;

/**
 * @author Peter Veentjer
 */
public abstract class AbstractInstrumentationPhase implements InstrumentationPhase {

    private final String name;

    public AbstractInstrumentationPhase(String name) {
        if (name == null) {
            throw new NullPointerException();
        }

        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public Clazz instrument(Environment environment, Clazz originalClazz) {
        Log log = environment.getLog();

        log.important("%s: transforming class %s", name, originalClazz.getName());

        doInit();

        return doInstrument(environment, originalClazz);
    }

    @Override
    public String toString() {
        return name;
    }

    protected void doInit() {
    }

    protected abstract Clazz doInstrument(Environment environment, Clazz clazz);
}