package net.rubygrapefruit.gradle.profiler;

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ProjectConnection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

class BuildInvoker {
    private final ProjectConnection projectConnection;
    private final List<String> jvmArgs;
    private final List<?> tasks;
    private final PidInstrumentation pidInstrumentation;

    public BuildInvoker(ProjectConnection projectConnection, List<?> tasks, List<String> jvmArgs, PidInstrumentation pidInstrumentation) {
        this.projectConnection = projectConnection;
        this.tasks = tasks;
        this.jvmArgs = jvmArgs;
        this.pidInstrumentation = pidInstrumentation;
    }

    public BuildResults runBuild() throws IOException {
        Timer timer = new Timer();
        run(projectConnection.newBuild(), build -> {
            build.forTasks(tasks.toArray(new String[0]));
            build.withArguments(pidInstrumentation.getArgs());
            build.setJvmArguments(jvmArgs);
            build.run();
            return null;
        });
        Duration executionTime = timer.elapsed();

        String pid = pidInstrumentation.getPidForLastBuild();
        System.out.println("Used daemon with pid " + pid);
        System.out.println("Execution time " + executionTime.toMillis() + "ms");

        return new BuildResults(executionTime, pid);
    }

    public static <T extends LongRunningOperation, R> R run(T operation, Function<T, R> function) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        operation.setStandardOutput(outputStream);
        operation.setStandardError(outputStream);
        try {
            return function.apply(operation);
        } catch (GradleConnectionException e) {
            System.out.println();
            System.out.println("ERROR: failed to run build.");
            System.out.println();
            System.out.append(new String(outputStream.toByteArray()));
            System.out.println();
            throw e;
        }
    }
}
