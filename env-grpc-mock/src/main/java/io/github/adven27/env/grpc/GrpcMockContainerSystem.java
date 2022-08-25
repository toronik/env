package io.github.adven27.env.grpc;

import io.github.adven27.env.core.ExternalSystem;
import io.github.adven27.env.core.ExternalSystemConfig;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.utility.MountableFile;

import java.util.List;
import java.util.Map;
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

    public GrpcMockContainerSystem(final int serviceId, final List<String> protos) {
        this(serviceId, null, protos, c -> { });
    }

    public GrpcMockContainerSystem(final int serviceId, final List<String> protos, Consumer<GrpcMockContainerSystem> afterStart) {
        this(serviceId, null, protos, afterStart);
    }

    public GrpcMockContainerSystem(final int serviceId, String wiremock, final List<String> protos, Consumer<GrpcMockContainerSystem> afterStart) {
        super("adven27/grpc-wiremock");
        this.serviceId = serviceId;
        this.afterStart = afterStart;

        protos.forEach(p -> this.withCopyFileToContainer(MountableFile.forClasspathResource(p), "/proto/" + p));
        Optional.ofNullable(wiremock).ifPresent(w -> this.withClasspathResourceMapping(w, "/wiremock", BindMode.READ_WRITE));
    }

    @Override
    public void start() {
        super.start();
        log.info("{} started on port {}; mock API port {}", getDockerImageName(), getMappedPort(50000), getMappedPort(8888));
        afterStart.accept(this);
    }

    @Override
    public void start(boolean fixedEnv) {
        withFixedExposedPort(fixedEnv ? serviceId + 10000 : findAvailableTcpPort(), 50000)
            .withFixedExposedPort(fixedEnv ? serviceId + 20000 : findAvailableTcpPort(), 8888)
            .waitingFor(forLogMessage(".*Started GrpcWiremock.*\\s", 1))
            .withStartupTimeout(ofSeconds(180))
            .withCreateContainerCmdModifier(cmd -> {
                String random = UUID.randomUUID().toString();
                cmd.withHostName("grpc-mock-" + serviceId + "-" + random);
                cmd.withName("grpc-mock-" + serviceId + "-" + random);
            });
        start();
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
    public ExternalSystemConfig getConfig() {
        return new Config(port(), mockPort());
    }

    @NotNull
    @Override
    public String describe() {
        return toString();
    }

    public static class Config extends ExternalSystemConfig {
        public static String PROP_GRPC_PORT = "env.grpc.port";
        public static String PROP_GRPC_MOCK_PORT = "env.grpc.mock.port";
        private final Integer grpcPort;
        private final Integer mockPort;

        public Config(int grpcPort, int mockPort) {
            super(Map.of(PROP_GRPC_PORT, String.valueOf(grpcPort), PROP_GRPC_MOCK_PORT, String.valueOf(mockPort)));
            this.grpcPort = grpcPort;
            this.mockPort = mockPort;
        }

        public Config() {
            this(50000, 8888);
        }

        public int getGrpcPort() {
            return grpcPort;
        }

        public int getMockPort() {
            return mockPort;
        }
    }
}
