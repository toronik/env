package io.github.adven27.env.grpc;

import io.github.adven27.env.core.ExternalSystem;
import io.github.adven27.env.core.PortsExposingStrategy;
import io.github.adven27.env.core.PortsExposingStrategy.SystemPropertyToggle;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.utility.MountableFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static io.github.adven27.env.core.Environment.findAvailableTcpPort;
import static java.time.Duration.ofSeconds;
import static org.testcontainers.containers.wait.strategy.Wait.forLogMessage;

public class GrpcMockContainerSystem extends FixedHostPortGenericContainer<GrpcMockContainerSystem> implements ExternalSystem {
    private static final Logger log = LoggerFactory.getLogger(GrpcMockContainerSystem.class);
    private final int serviceId;
    private final Consumer<GrpcMockContainerSystem> afterStart;

    public GrpcMockContainerSystem(
        final int serviceId, PortsExposingStrategy portsExposingStrategy, final List<String> protos
    ) {
        this(serviceId, portsExposingStrategy, null, protos, c -> { });
    }

    public GrpcMockContainerSystem(
        final int serviceId,
        PortsExposingStrategy portsExposingStrategy,
        final List<String> protos,
        Consumer<GrpcMockContainerSystem> afterStart
    ) {
        this(serviceId, portsExposingStrategy, null, protos, afterStart);
    }

    public GrpcMockContainerSystem(final int serviceId, final List<String> protos) {
        this(serviceId, new SystemPropertyToggle(), null, protos, c -> { });
    }

    public GrpcMockContainerSystem(final int serviceId, PortsExposingStrategy portsExposingStrategy, String wiremock, final List<String> protos, Consumer<GrpcMockContainerSystem> afterStart) {
        super("adven27/grpc-wiremock");
        this.serviceId = serviceId;
        this.afterStart = afterStart;
        this.withFixedExposedPort(findAndSetBasePort(portsExposingStrategy.fixedPorts()) + serviceId, 50000)
            .waitingFor(forLogMessage(".*Started GrpcWiremock.*\\s", 1))
            .withStartupTimeout(ofSeconds(180))
            .withCreateContainerCmdModifier(cmd -> {
                String random = UUID.randomUUID().toString();
                cmd.withHostName("grpc-mock-" + serviceId + "-" + random);
                cmd.withName("grpc-mock-" + serviceId + "-" + random);
            });

        protos.forEach(p -> this.withCopyFileToContainer(MountableFile.forClasspathResource(p), "/proto/" + p));
        Optional.ofNullable(wiremock).ifPresent(w -> this.withClasspathResourceMapping(w, "/wiremock", BindMode.READ_WRITE));

        if (portsExposingStrategy.fixedPorts()) {
            withFixedExposedPort(20000 + serviceId, 8888);
        }
    }

    @Override
    public void start() {
        super.start();
        log.info("{} started on port {}; mock API port {}", getDockerImageName(), getMappedPort(50000), getMappedPort(8888));
        afterStart.accept(this);
    }

    private static int findAndSetBasePort(boolean fixedEnv) {
        return fixedEnv ? 10000 : findAvailableTcpPort();
    }

    private int mockPort() {
        return isRunning() ? getMappedPort(8888) : 20000 + serviceId;
    }

    private int port() {
        return isRunning() ? getMappedPort(50000) : 10000 + serviceId;
    }

    public int getServiceId() {
        return serviceId;
    }

    @Override
    public boolean running() {
        return isRunning();
    }

    @NotNull
    @Override
    public String describe() {
        return toString();
    }

    @NotNull
    @Override
    public Object config() {
        return new Config(port(), mockPort());
    }

    public static class Config {
        private final Integer grpcPort;
        private final Integer mockPort;

        public Config(int grpcPort, int mockPort) {
            this.grpcPort = grpcPort;
            this.mockPort = mockPort;
        }

        public Config() {
            this.grpcPort = 50000;
            this.mockPort = 8888;
        }

        public int getGrpcPort() {
            return grpcPort;
        }

        public int getMockPort() {
            return mockPort;
        }
    }
}
