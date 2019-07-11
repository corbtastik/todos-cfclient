package io.todos.cf.cfclient;

import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.PushApplicationRequest;
import org.cloudfoundry.operations.applications.SetEnvironmentVariableApplicationRequest;
import org.cloudfoundry.operations.applications.StartApplicationRequest;
import org.cloudfoundry.operations.organizations.OrganizationSummary;
import org.cloudfoundry.operations.routes.MapRouteRequest;
import org.cloudfoundry.operations.routes.UnmapRouteRequest;
import org.cloudfoundry.operations.services.BindServiceInstanceRequest;
import org.cloudfoundry.operations.services.ServiceInstanceSummary;
import org.cloudfoundry.operations.spaces.SpaceSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Configuration
@ShellComponent
public class ShellCommands {
    // local folder with 3 sample jars
    @Value("${jars.folder}")
    String jarsFolder;
    // cf api
    @Value("${cf.api}")
    String cfApi;
    // cf domain, default for public networking
    @Value("${cf.domain}")
    String cfDomain;
    // cf default memory for apps
    @Value("${cf.memory:1024}")
    Integer cfMemory;
    // cf operations API
    private CloudFoundryOperations cf;

    // autowire operations instance
    public ShellCommands(@Autowired CloudFoundryOperations operations) {
        this.cf = operations;
    }

    @ShellMethod("push with api")
    public void pushApp(
            @ShellOption(help = "tag for hostname") String tag,
            @ShellOption(help = "version (ex: 1.0.0.RELEASE, 1.0.0.SNAP)", defaultValue = "1.0.0.SNAP") String version) {

        if (tag.length() < 1) {
            tag = UUID.randomUUID().toString().substring(0, 8);
        }

        // push api
        pushApplication(tag + "-todos-api",
                Paths.get(jarsFolder + "/todos-api-" + version + ".jar"))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-api").build())).subscribe();

        // push webui
        pushApplication(tag + "-todos-webui",
                Paths.get(jarsFolder, "todos-webui-" + version + ".jar").toFile().toPath())
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-webui").build())).subscribe();

        // push edge and manually config UI and API endpoints in edge's ENV
        pushApplication(tag + "-todos-edge",
                Paths.get(jarsFolder, "todos-edge-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-edge")
                                .variableName("TODOS_UI_ENDPOINT")
                                .variableValue("http://" + tag + "-todos-webui." + cfDomain)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-edge")
                                .variableName("TODOS_API_ENDPOINT")
                                .variableValue("http://" + tag + "-todos-api." + cfDomain)
                                .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-edge").build())).subscribe();
    }

    @ShellMethod("push with private networking")
    public void pushInternal(
            @ShellOption(help = "tag for hostname") String tag,
            @ShellOption(help = "version (ex: 1.0.0.RELEASE, 1.0.0.SNAP)", defaultValue = "1.0.0.SNAP") String version,
            @ShellOption(help = "internal domain (ex: apps.internal", defaultValue = "apps.internal") String internalDomain) {

        // push api with internal route
        pushApplication(tag + "-todos-api",
                Paths.get(jarsFolder, "todos-api-" + version + ".jar").toFile().toPath())
                .then(this.cf.routes()
                        .map(MapRouteRequest.builder()
                                .applicationName(tag + "-todos-api")
                                .domain(internalDomain)
                                .host(tag + "-todos-api")
                                .build()))
                .then(this.cf.routes()
                        .unmap(UnmapRouteRequest.builder()
                                .applicationName(tag + "-todos-api")
                                .domain(this.cfDomain)
                                .host(tag + "-todos-api")
                                .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-api").build())).subscribe();

        // push webui with internal route
        pushApplication(tag + "-todos-webui",
                Paths.get(jarsFolder, "todos-webui-" + version + ".jar").toFile().toPath())
                .then(this.cf.routes()
                        .map(MapRouteRequest.builder()
                                .applicationName(tag + "-todos-webui")
                                .domain(internalDomain)
                                .host(tag + "-todos-webui")
                                .build()))
                .then(this.cf.routes()
                        .unmap(UnmapRouteRequest.builder()
                                .applicationName(tag + "-todos-webui")
                                .domain(this.cfDomain)
                                .host(tag + "-todos-webui")
                                .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-webui").build())).subscribe();

        // push edge and config UI and API endpoints in edge's ENV
        pushApplication(tag + "-todos-edge",
                Paths.get(jarsFolder, "todos-edge-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-edge")
                                .variableName("TODOS_UI_ENDPOINT")
                                .variableValue("http://" + tag + "-todos-webui.apps.internal:8080")
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-edge")
                                .variableName("TODOS_API_ENDPOINT")
                                .variableValue("http://" + tag + "-todos-api.apps.internal:8080")
                                .build()))
                .then(this.cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-edge").build())).subscribe();
    }

    @ShellMethod("push with spring-cloud")
    public void pushScs(
            @ShellOption(help = "tag for hostname") String tag,
            @ShellOption(help = "version (ex: 1.0.0.RELEASE, 1.0.0.SNAP)", defaultValue = "1.0.0.SNAP") String version,
            @ShellOption(help = "config-service", defaultValue = "todos-config") String configServiceInstance,
            @ShellOption(help = "registry-service", defaultValue = "todos-registry") String registryServiceInstance) {

        if (tag.length() < 1) {
            tag = UUID.randomUUID().toString().substring(0, 8);
        }

        pushApplication(tag + "-todos-api",
                Paths.get(jarsFolder, "todos-api-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-api")
                                .variableName("TRUST_CERTS")
                                .variableValue(cfApi)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-api")
                                .variableName("SPRING_APPLICATION_NAME")
                                .variableValue(tag + "-todos-api")
                                .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-api")
                        .serviceInstanceName(configServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-api")
                        .serviceInstanceName(registryServiceInstance)
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-api").build())).subscribe();

        pushApplication(tag + "-todos-webui",
                Paths.get(jarsFolder, "todos-webui-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-webui")
                                .variableName("TRUST_CERTS")
                                .variableValue(cfApi)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-webui")
                                .variableName("SPRING_APPLICATION_NAME")
                                .variableValue(tag + "-todos-webui")
                                .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-webui")
                        .serviceInstanceName(configServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-webui")
                        .serviceInstanceName(registryServiceInstance)
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-webui").build())).subscribe();

        pushApplication(tag + "-todos-edge",
                Paths.get(jarsFolder, "todos-edge-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-edge")
                                .variableName("TRUST_CERTS")
                                .variableValue(cfApi)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-edge")
                                .variableName("SPRING_APPLICATION_NAME")
                                .variableValue(tag + "-todos-edge")
                                .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-edge")
                        .serviceInstanceName(configServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-edge")
                        .serviceInstanceName(registryServiceInstance)
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-edge").build())).subscribe();
    }

    @ShellMethod("push with mysql")
    public void pushMySQL(
            @ShellOption(help = "tag for hostname") String tag,
            @ShellOption(help = "version (ex: 1.0.0.RELEASE, 1.0.0.SNAP)", defaultValue = "1.0.0.SNAP") String version,
            @ShellOption(help = "mysql service instance name (ex: todos-database)", defaultValue = "todos-database") String serviceInstance) {

        // push mysql backend app
        pushApplication(tag + "-todos-mysql",
                Paths.get(jarsFolder, "todos-mysql-" + version + ".jar").toFile().toPath())
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-mysql")
                        .serviceInstanceName(serviceInstance)
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-mysql").build())).subscribe();

        // push webui
        pushApplication(tag + "-todos-webui",
                Paths.get(jarsFolder, "todos-webui-" + version + ".jar").toFile().toPath())
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-webui").build())).subscribe();

        // push edge and manually config UI and API endpoints in edge's ENV
        pushApplication(tag + "-todos-edge",
                Paths.get(jarsFolder, "todos-edge-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-edge")
                                .variableName("TODOS_UI_ENDPOINT")
                                .variableValue("http://" + tag + "-todos-webui." + cfDomain)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-edge")
                                .variableName("TODOS_API_ENDPOINT")
                                .variableValue("http://" + tag + "-todos-mysql." + cfDomain)
                                .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-edge").build())).subscribe();
    }

    @ShellMethod("push with spring-cloud and redis")
    public void pushScsMySQL(
            @ShellOption(help = "tag for hostname") String tag,
            @ShellOption(help = "version (ex: 1.0.0.RELEASE, 1.0.0.SNAP)", defaultValue = "1.0.0.SNAP") String version,
            @ShellOption(help = "config-service", defaultValue = "todos-config") String configServiceInstance,
            @ShellOption(help = "registry-service", defaultValue = "todos-registry") String registryServiceInstance,
            @ShellOption(help = "mysql service instance name (ex: todos-database)", defaultValue = "todos-database") String databaseServiceInstance) {

        if (tag.length() < 1) {
            tag = UUID.randomUUID().toString().substring(0, 8);
        }

        // push mysql backend app
        pushApplication(tag + "-todos-mysql",
                Paths.get(jarsFolder, "todos-mysql-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-mysql")
                                .variableName("TRUST_CERTS")
                                .variableValue(cfApi)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-mysql")
                                .variableName("SPRING_APPLICATION_NAME")
                                .variableValue(tag + "-todos-mysql")
                                .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-mysql")
                        .serviceInstanceName(databaseServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-mysql")
                        .serviceInstanceName(configServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-mysql")
                        .serviceInstanceName(registryServiceInstance)
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-mysql").build())).subscribe();

        pushApplication(tag + "-todos-webui",
                Paths.get(jarsFolder, "todos-webui-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-webui")
                                .variableName("TRUST_CERTS")
                                .variableValue(cfApi)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-webui")
                                .variableName("SPRING_APPLICATION_NAME")
                                .variableValue(tag + "-todos-webui")
                                .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-webui")
                        .serviceInstanceName(configServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-webui")
                        .serviceInstanceName(registryServiceInstance)
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-webui").build())).subscribe();

        pushApplication(tag + "-todos-edge",
                Paths.get(jarsFolder, "todos-edge-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-edge")
                                .variableName("TRUST_CERTS")
                                .variableValue(cfApi)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-edge")
                                .variableName("SPRING_APPLICATION_NAME")
                                .variableValue(tag + "-todos-edge")
                                .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-edge")
                        .serviceInstanceName(configServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-edge")
                        .serviceInstanceName(registryServiceInstance)
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-edge").build())).subscribe();
    }

    @ShellMethod("push with redis")
    public void pushRedis(
            @ShellOption(help = "tag for hostname") String tag,
            @ShellOption(help = "version (ex: 1.0.0.RELEASE, 1.0.0.SNAP)", defaultValue = "1.0.0.SNAP") String version,
            @ShellOption(help = "redis service instance name (ex: todos-redis)", defaultValue = "todos-redis") String serviceInstance) {

        // push redis backend app
        pushApplication(tag + "-todos-redis",
                Paths.get(jarsFolder, "todos-redis-" + version + ".jar").toFile().toPath())
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-redis")
                        .serviceInstanceName(serviceInstance)
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-redis").build())).subscribe();

        // push webui
        pushApplication(tag + "-todos-webui",
                Paths.get(jarsFolder, "todos-webui-" + version + ".jar").toFile().toPath())
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-webui").build())).subscribe();

        // push edge and manually config UI and API endpoints in edge's ENV
        pushApplication(tag + "-todos-edge",
                Paths.get(jarsFolder, "todos-edge-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-edge")
                                .variableName("TODOS_UI_ENDPOINT")
                                .variableValue("http://" + tag + "-todos-webui." + cfDomain)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-edge")
                                .variableName("TODOS_API_ENDPOINT")
                                .variableValue("http://" + tag + "-todos-redis." + cfDomain)
                                .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-edge").build())).subscribe();
    }

    @ShellMethod("push with spring-cloud and redis")
    public void pushScsRedis(
            @ShellOption(help = "tag for hostname") String tag,
            @ShellOption(help = "version (ex: 1.0.0.RELEASE, 1.0.0.SNAP)", defaultValue = "1.0.0.SNAP") String version,
            @ShellOption(help = "config-service", defaultValue = "todos-config") String configServiceInstance,
            @ShellOption(help = "registry-service", defaultValue = "todos-registry") String registryServiceInstance,
            @ShellOption(help = "redis service instance name (ex: todos-redis)", defaultValue = "todos-redis") String redisServiceInstance) {

        if (tag.length() < 1) {
            tag = UUID.randomUUID().toString().substring(0, 8);
        }

        // push redis backend app
        pushApplication(tag + "-todos-redis",
                Paths.get(jarsFolder, "todos-redis-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-redis")
                                .variableName("TRUST_CERTS")
                                .variableValue(cfApi)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-redis")
                                .variableName("SPRING_APPLICATION_NAME")
                                .variableValue(tag + "-todos-redis")
                                .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-redis")
                        .serviceInstanceName(redisServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-redis")
                        .serviceInstanceName(configServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-redis")
                        .serviceInstanceName(registryServiceInstance)
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-redis").build())).subscribe();

        pushApplication(tag + "-todos-webui",
                Paths.get(jarsFolder, "todos-webui-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-webui")
                                .variableName("TRUST_CERTS")
                                .variableValue(cfApi)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-webui")
                                .variableName("SPRING_APPLICATION_NAME")
                                .variableValue(tag + "-todos-webui")
                                .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-webui")
                        .serviceInstanceName(configServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-webui")
                        .serviceInstanceName(registryServiceInstance)
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-webui").build())).subscribe();

        pushApplication(tag + "-todos-edge",
                Paths.get(jarsFolder, "todos-edge-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-edge")
                                .variableName("TRUST_CERTS")
                                .variableValue(cfApi)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-edge")
                                .variableName("SPRING_APPLICATION_NAME")
                                .variableValue(tag + "-todos-edge")
                                .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-edge")
                        .serviceInstanceName(configServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-edge")
                        .serviceInstanceName(registryServiceInstance)
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-edge").build())).subscribe();
    }

    @ShellMethod("push with spring-cloud and look-aside caching")
    public void pushLookaside(
            @ShellOption(help = "tag for hostname") String tag,
            @ShellOption(help = "version (ex: 1.0.0.RELEASE, 1.0.0.SNAP)", defaultValue = "1.0.0.SNAP") String version,
            @ShellOption(help = "config-service", defaultValue = "todos-config") String configServiceInstance,
            @ShellOption(help = "registry-service", defaultValue = "todos-registry") String registryServiceInstance,
            @ShellOption(help = "mysql service instance name (ex: todos-database)", defaultValue = "todos-database") String databaseServiceInstance,
            @ShellOption(help = "redis service instance name (ex: todos-redis)", defaultValue = "todos-redis") String redisServiceInstance,
            @ShellOption(help = "messaging service instance name (ex: todos-messaging)", defaultValue = "todos-messaging") String messagingServiceInstance) {

        if (tag.length() < 1) {
            tag = UUID.randomUUID().toString().substring(0, 8);
        }

        // push scs app backend app
        pushApplication(tag + "-todos-app",
                Paths.get(jarsFolder, "todos-app-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-app")
                                .variableName("TRUST_CERTS")
                                .variableValue(cfApi)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-app")
                                .variableName("SPRING_APPLICATION_NAME")
                                .variableValue(tag + "-todos-app")
                                .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-app")
                        .serviceInstanceName(configServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-app")
                        .serviceInstanceName(registryServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-app")
                        .serviceInstanceName(messagingServiceInstance)
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-app").build())).subscribe();

        // push mysql backend for Sor
        pushApplication(tag + "-todos-mysql",
                Paths.get(jarsFolder, "todos-mysql-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-mysql")
                                .variableName("TRUST_CERTS")
                                .variableValue(cfApi)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-mysql")
                                .variableName("SPRING_APPLICATION_NAME")
                                .variableValue(tag + "-todos-mysql")
                                .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-mysql")
                        .serviceInstanceName(databaseServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-mysql")
                        .serviceInstanceName(configServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-mysql")
                        .serviceInstanceName(registryServiceInstance)
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-mysql").build())).subscribe();

        // push redis backend app for Cache
        pushApplication(tag + "-todos-redis",
                Paths.get(jarsFolder, "todos-redis-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-redis")
                                .variableName("TRUST_CERTS")
                                .variableValue(cfApi)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-redis")
                                .variableName("SPRING_APPLICATION_NAME")
                                .variableValue(tag + "-todos-redis")
                                .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-redis")
                        .serviceInstanceName(redisServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-redis")
                        .serviceInstanceName(configServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-redis")
                        .serviceInstanceName(registryServiceInstance)
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-redis").build())).subscribe();

        pushApplication(tag + "-todos-webui",
                Paths.get(jarsFolder, "todos-webui-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-webui")
                                .variableName("TRUST_CERTS")
                                .variableValue(cfApi)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-webui")
                                .variableName("SPRING_APPLICATION_NAME")
                                .variableValue(tag + "-todos-webui")
                                .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-webui")
                        .serviceInstanceName(configServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-webui")
                        .serviceInstanceName(registryServiceInstance)
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-webui").build())).subscribe();

        pushApplication(tag + "-todos-edge",
                Paths.get(jarsFolder, "todos-edge-" + version + ".jar").toFile().toPath())
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-edge")
                                .variableName("TRUST_CERTS")
                                .variableValue(cfApi)
                                .build()))
                .then(this.cf.applications()
                        .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                .name(tag + "-todos-edge")
                                .variableName("SPRING_APPLICATION_NAME")
                                .variableValue(tag + "-todos-edge")
                                .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-edge")
                        .serviceInstanceName(configServiceInstance)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-edge")
                        .serviceInstanceName(registryServiceInstance)
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-edge").build())).subscribe();
    }

    @ShellMethod("list jars")
    public List<String> jars() {
        return Arrays.asList(Paths.get(jarsFolder).toFile().list());
    }

    @ShellMethod("list orgs")
    public List<String> orgs() {
        return cf.organizations().list().map(OrganizationSummary::getName).collectList().block();
    }

    @ShellMethod("list spaces")
    public List<String> spaces() {
        return cf.spaces().list().map(SpaceSummary::getName).collectList().block();
    }

    @ShellMethod("list apps")
    public List<String> apps() {
        return cf.applications().list().map(ApplicationSummary::getName).collectList().block();
    }

    @ShellMethod("list services")
    public List<String> services() {
        return cf.services().listInstances().map(ServiceInstanceSummary::getName).collectList().block();
    }

    private Mono<Void> pushApplication(String name, Path application) {
        return cf.applications()
                .push(PushApplicationRequest.builder()
                        .noStart(true)
                        .memory(this.cfMemory)
                        .name(name)
                        .path(application)
                        .build());
    }
}
