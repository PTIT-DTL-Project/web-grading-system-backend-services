package vn.edu.ptit.web_grading_system.executor_service.service;

import vn.edu.ptit.web_grading_system.executor_service.Constant;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure compose-file logic: picks the app service, rewrites its published port,
 * injects resource limits, and rejects unsafe directives. No Docker involved,
 * so every branch is unit-testable.
 */
public final class DockerComposePatcher
{
    private DockerComposePatcher()
    {
    }

    public record EffectiveCompose(String serviceName, int containerPort, Path composeFile)
    {
    }

    public static EffectiveCompose writeEffectiveCompose(Path workDir, String gradingStrategy,
            String template, int appPort, int dockerComposePort, Double maxCpu, Integer maxMemoryMb)
            throws IOException
    {
        Path composeFile = workDir.resolve(Constant.Strategy.DOCKER_COMPOSE_YML);
        if (Constant.Strategy.LECTURER_DOCKER_COMPOSE.equals(gradingStrategy))
        {
            if (template == null || template.isBlank())
            {
                throw new IllegalStateException(Constant.Message.NO_COMPOSE_TEMPLATE);
            }
            Files.writeString(composeFile, template);
        }
        else
        {
            if (!Files.isRegularFile(composeFile))
            {
                Path alt = workDir.resolve(Constant.Strategy.DOCKER_COMPOSE_YAML);
                if (Files.isRegularFile(alt))
                {
                    composeFile = alt;
                }
                else
                {
                    throw new IllegalStateException(Constant.Message.NO_COMPOSE_FILE);
                }
            }
        }
        Map<String, Object> compose = load(composeFile);
        Map<String, Object> services = servicesOf(compose);
        if (services.isEmpty())
        {
            throw new IllegalStateException(Constant.Message.NO_SERVICES);
        }
        String appService = firstServiceWithPorts(services);
        int containerPort = dockerComposePort > 0 ? dockerComposePort : 8080;
        for (Map.Entry<String, Object> entry : services.entrySet())
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> service = (Map<String, Object>) entry.getValue();
            rejectUnsafe(entry.getKey(), service);
            applyLimits(service, maxCpu, maxMemoryMb);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> app = (Map<String, Object>) services.get(appService);
        app.put(Constant.DockerCompose.PORTS, List.of(appPort + ":" + containerPort));
        try (Writer writer = Files.newBufferedWriter(composeFile))
        {
            new Yaml().dump(compose, writer);
        }
        return new EffectiveCompose(appService, containerPort, composeFile);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(Path composeFile) throws IOException
    {
        try (InputStream in = Files.newInputStream(composeFile))
        {
            Object loaded = new Yaml().load(in);
            if (!(loaded instanceof Map))
            {
                throw new IllegalStateException(Constant.Message.NO_TOP_LEVEL);
            }
            return (Map<String, Object>) loaded;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> servicesOf(Map<String, Object> compose)
    {
        Object services = compose.get(Constant.DockerCompose.SERVICES);
        if (!(services instanceof Map))
        {
            throw new IllegalStateException(Constant.Message.NO_SERVICES_SECTION);
        }
        return (Map<String, Object>) services;
    }

    @SuppressWarnings("unchecked")
    private static String firstServiceWithPorts(Map<String, Object> services)
    {
        for (Map.Entry<String, Object> entry : services.entrySet())
        {
            Object service = entry.getValue();
            if (service instanceof Map<?, ?> map && map.get(Constant.DockerCompose.PORTS) != null)
            {
                return entry.getKey();
            }
        }
        return services.keySet().iterator().next();
    }

    private static void rejectUnsafe(String name, Map<String, Object> service)
    {
        if (Boolean.TRUE.equals(service.get(Constant.DockerCompose.PRIVILEGED)))
        {
            throw new IllegalStateException(Constant.Message.PRIVILEGED_MODE_PREFIX + name);
        }
        Object volumes = service.get(Constant.DockerCompose.VOLUMES);
        if (volumes instanceof List<?> list)
        {
            for (Object volume : list)
            {
                if (volume != null && volume.toString().contains(Constant.DockerCompose.DOCKER_SOCK))
                {
                    throw new IllegalStateException(Constant.Message.MOUNTS_DOCKER_SOCK_PREFIX + name);
                }
            }
        }
    }

    private static void applyLimits(Map<String, Object> service, Double maxCpu, Integer maxMemoryMb)
    {
        Map<String, Object> limits = new LinkedHashMap<>();
        if (maxCpu != null && maxCpu > 0)
        {
            limits.put(Constant.DockerCompose.CPUS, String.valueOf(maxCpu));
        }
        if (maxMemoryMb != null && maxMemoryMb > 0)
        {
            limits.put(Constant.DockerCompose.MEMORY, maxMemoryMb + "M");
        }
        if (limits.isEmpty())
        {
            return;
        }
        Object deploy = service.computeIfAbsent(Constant.DockerCompose.DEPLOY, k -> new LinkedHashMap<String, Object>());
        if (deploy instanceof Map<?, ?> deployMap)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> deployTyped = (Map<String, Object>) deployMap;
            deployTyped.put(Constant.DockerCompose.RESOURCES, Map.of(Constant.DockerCompose.LIMITS, limits));
        }
    }
}
