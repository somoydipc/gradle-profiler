package org.gradle.trace;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.trace.listener.BuildOperationListenerAdapter;
import org.gradle.trace.monitoring.GCMonitoring;
import org.gradle.trace.monitoring.SystemMonitoring;
import org.gradle.trace.util.TimeUtil;

import java.io.File;
import java.util.HashMap;

import static org.gradle.trace.util.ReflectionUtil.invokerGetter;

public class GradleTracingPlugin {
    private static final String CATEGORY_PHASE = "BUILD_PHASE";
    private static final String PHASE_BUILD = "build duration";

    private final BuildRequestMetaData buildRequestMetaData;
    private final TraceResult traceResult;
    private final SystemMonitoring systemMonitoring = new SystemMonitoring();
    private final GCMonitoring gcMonitoring = new GCMonitoring();
    private final BuildOperationListenerAdapter buildOperationListener;

    private GradleTracingPlugin(GradleInternal gradle, File traceFolder, String traceFilePattern) {
        this.buildRequestMetaData = gradle.getServices().get(BuildRequestMetaData.class);
        traceResult = new TraceResult(getTraceFile(traceFolder, traceFilePattern));
        systemMonitoring.start(traceResult);
        gcMonitoring.start(traceResult);
        buildOperationListener = BuildOperationListenerAdapter.create(gradle, traceResult);
        gradle.addListener(new TraceFinalizerAdapter(gradle));
    }

    private File getTraceFile(File traceFolder, String traceFilePatter) {
        String phase = System.getProperty("org.gradle.profiler.phase.display.name");
        String build = System.getProperty("org.gradle.profiler.number");
        int invocation = 1;
        File traceFile;
        do {
            String traceFileName = traceFilePatter.replace("{phase}", phase)
                .replace("{build}", build)
                .replace("{invocation}", String.valueOf(invocation));
            traceFile = new File(traceFolder.getAbsoluteFile(), traceFileName);
            invocation++;
        } while (traceFile.exists());
        return traceFile;
    }

    /**
     * org.gradle.profiler.chrometrace.ChromeTraceInstrumentation writes a build init script that calls this
     */
    @SuppressWarnings("unused")
    public static void start(GradleInternal gradle, File traceFile, String traceFilePattern) {
        new GradleTracingPlugin(gradle, traceFile, traceFilePattern);
    }

    private class TraceFinalizerAdapter extends BuildAdapter {
        private final Gradle gradle;

        private TraceFinalizerAdapter(Gradle gradle) {
            this.gradle = gradle;
        }

        @Override
        public void buildFinished(BuildResult result) {
            systemMonitoring.stop();
            gcMonitoring.stop();
            buildOperationListener.remove();

            traceResult.start(PHASE_BUILD, CATEGORY_PHASE, TimeUtil.toNanoTime(getStartTime()));
            traceResult.finish(PHASE_BUILD, System.nanoTime(), new HashMap<>());
            traceResult.finalizeTraceFile();

            gradle.removeListener(this);
        }

        private long getStartTime() {
            try {
                return buildRequestMetaData.getStartTime();
            } catch (NoSuchMethodError e) {
                return (long) invokerGetter(invokerGetter(buildRequestMetaData, "getBuildTimeClock"), "getStartTime");
            }
        }
    }
}
