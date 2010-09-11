package org.multiverse.stms.beta.benchmarks;

/**
 * @author Peter Veentjer
 */
public class Result {
    public final int processorCount;
    public final double performance;

    public Result(int processorCount, double performance) {
        this.processorCount = processorCount;
        this.performance = performance;
    }
}