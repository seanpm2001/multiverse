package org.multiverse.javaagent;

import org.multiverse.MultiverseConstants;
import org.multiverse.instrumentation.compiler.ClazzCompiler;
import org.multiverse.instrumentation.compiler.SystemOutLog;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static java.lang.String.format;
import static java.lang.System.getProperty;

/**
 * The MultiverseJavaAgent is responsible for transforming classes when they are loaded
 * using the javaagent technology.
 * <p/>
 * This agent is a general purpose agent that can be used for different STM implementation. And
 * can be configured through System.properties.
 * properties:
 * org.multiverse.javaagent.compiler=org.multiverse.stms.alpha.instrumentation.AlphaCompiler
 * org.multiverse.javaagent.dumpBytecode=true/false
 * org.multiverse.javaagent.verbose=true/false
 * org.multiverse.javaagent.dumpDirectory=directory for dumping classfiles (defaults to the tmp dir)
 * org.multiverse.javaagent.included=
 * org.multiverse.javaagent.excluded=
 * <p/>
 *
 * @author Peter Veentjer
 */
public final class MultiverseJavaAgent {

    public final static String KEY = "org.multiverse.javaagent.compiler";

    public static void premain(String agentArgs, Instrumentation inst) throws UnmodifiableClassException {
        printMultiverseJavaAgentInfo();

        ClazzCompiler compiler = loadClazzCompiler();
        inst.addTransformer(new MultiverseClassFileTransformer(compiler));
    }

    private static ClazzCompiler loadClazzCompiler() {
        ClazzCompiler compiler = createClazzCompiler();

        boolean verbose = getSystemBooleanProperty("verbose", false);
        if (verbose) {
            compiler.setLog(new SystemOutLog());
        }

        compiler.setFiler(new JavaAgentFiler());
        boolean dumpBytecode = getSystemBooleanProperty("dumpBytecode", false);
        compiler.setDumpBytecode(dumpBytecode);
        if (dumpBytecode) {
            String tmpDir = System.getProperty("java.io.tmpdir");
            File dumpDirectory = new File(getSystemProperty("dumpDirectory", tmpDir));

            System.out.println(format("MultiverseJavaAgent: Bytecode will be dumped to '%s'", dumpDirectory.getAbsolutePath()));

            compiler.setDumpDirectory(dumpDirectory);
        }
        return compiler;
    }

    private static void printMultiverseJavaAgentInfo() {
        System.out.println("MultiverseJavaAgent: Starting Multiverse JavaAgent 0.5-SNAPSHOT");

        if (MultiverseConstants.___SANITY_CHECKS_ENABLED) {
            System.out.println("Sanity checks are enabled.");
        }
    }

    private static ClazzCompiler createClazzCompiler() {
        String compilerClassName = getSystemProperty(
                "compiler",
                "org.multiverse.stms.alpha.instrumentation.AlphaClazzCompiler");

        System.out.println(format("MultiverseJavaAgent: Using compiler '%s'", compilerClassName));

        Constructor constructor = getMethod(compilerClassName);
        try {
            return (ClazzCompiler) constructor.newInstance();
        } catch (IllegalAccessException e) {
            String msg = format("Failed to initialize Compiler through System property '%s' with value '%s'." +
                    "The constructor is not accessable.",
                    KEY, compilerClassName);
            throw new IllegalArgumentException(msg, e);
        } catch (InvocationTargetException e) {
            String msg = format("Failed to initialize Compiler through System property '%s' with value '%s'." +
                    "The constructor threw an exception.",
                    KEY, compilerClassName);
            System.out.println(msg);
            throw new IllegalArgumentException(msg, e);
        } catch (InstantiationException e) {
            String msg = format("Failed to initialize Compiler through System property '%s' with value '%s'." +
                    "The class could not be instantiated.",
                    KEY, compilerClassName);
            System.out.println(msg);
            throw new IllegalArgumentException(msg, e);
        }
    }

    private static boolean getSystemBooleanProperty(String property, boolean defaultValue) {
        String value = getSystemProperty(property, "" + defaultValue);

        if (value.equals("true")) {
            return true;
        } else if (value.equals("false")) {
            return false;
        } else {
            String msg = format("property %s with value '%s' is not a boolean (true/false).", property, value);
            throw new IllegalArgumentException(msg);
        }
    }

    private static String getSystemProperty(String property, String defaultValue) {
        String fullProperty = "org.multiverse.javaagent." + property;
        return getProperty(fullProperty, defaultValue);
    }

    private static Constructor getMethod(String className) {
        Class compilerClazz;
        try {
            compilerClazz = Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            String msg = format("Failed to initialize Compiler through System property '%s' with value '%s'." +
                    "Is not an existing class (it can't be found using the Thread.currentThread.getContextClassLoader).",
                    KEY, className);
            System.out.println(msg);
            throw new IllegalArgumentException(msg, e);
        }

        if (!ClazzCompiler.class.isAssignableFrom(compilerClazz)) {
            String msg = format("Failed to initialize Compiler through System property '%s' with value '%s'." +
                    "Is not an subclass of org.multiverse.compiler.ClazzCompiler).",
                    KEY, className);
            System.out.println(msg);
            throw new IllegalArgumentException(msg);
        }

        Constructor method;
        try {
            method = compilerClazz.getConstructor();
        } catch (NoSuchMethodException e) {
            String msg = format("Failed to initialize Compiler through System property '%s' with value '%s'." +
                    "A no arg constructor is not found.",
                    KEY, compilerClazz);
            System.out.println(msg);
            throw new IllegalArgumentException(msg, e);
        }

        return method;
    }
}