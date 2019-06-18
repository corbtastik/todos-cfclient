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
    @Value("${jars.folder}") String jarsFolder;
    // cf api
    @Value("${cf.api}") String cfApi;
    // cf domain
    @Value("${cf.domain}") String cfDomain;
    // cf operations API
    private CloudFoundryOperations cf;

    // autowire operations instance
    public ShellCommands(@Autowired CloudFoundryOperations operations) {
        this.cf = operations;
    }

    @ShellMethod("push to pcf")
    public void push(
        @ShellOption(help = "tag for hostname") String tag,
        @ShellOption(help = "version (ex: 1.0.0.RELEASE, 1.0.0.SNAP)", defaultValue = "1.0.0.SNAP") String version,
        @ShellOption(help = "kind of app - simple|scs", defaultValue = "simple") String kind,
        @ShellOption(help = "networking for app - public|private", defaultValue = "public") String networking) {

        if(tag.length() < 1) {
            tag = UUID.randomUUID().toString().substring(0,8);
        }

        if("scs".equalsIgnoreCase(kind)) {
            deploySpringCloudApps(tag, version, networking);
        } else {
            deployApps(tag, version, networking);
        }
    }

    private void deployApps(String tag, String version, String networking) {
        if("private".equalsIgnoreCase(networking)) {
            deployPrivateApps(tag, version);
        } else {
            deployPublicApps(tag, version);
        }
    }

    private void deployPrivateApps(String tag, String version) {
        // push api with internal route
        pushApplication(tag + "-todos-api",
            Paths.get(jarsFolder, "todos-api-" + version + ".jar").toFile().toPath(), true)
                .then(this.cf.routes()
                    .map(MapRouteRequest.builder()
                        .applicationName(tag + "-todos-api")
                        .domain("apps.internal")
                        .host(tag + "-todos-api")
                        .build()))
                .then(this.cf.routes()
                    .unmap(UnmapRouteRequest.builder()
                        .applicationName(tag + "-todos-api")
                        .domain("apps.retro.io")
                        .host(tag + "-todos-api")
                        .build()))
                .then(cf.applications()
                    .start(StartApplicationRequest.builder()
                        .name(tag + "-todos-api").build())).subscribe();

        // push webui with internal route
        pushApplication(tag + "-todos-webui",
            Paths.get(jarsFolder, "todos-webui-" + version + ".jar").toFile().toPath(), true)
                .then(this.cf.routes()
                    .map(MapRouteRequest.builder()
                        .applicationName(tag + "-todos-webui")
                        .domain("apps.internal")
                        .host(tag + "-todos-webui")
                        .build()))
                .then(this.cf.routes()
                    .unmap(UnmapRouteRequest.builder()
                        .applicationName(tag + "-todos-webui")
                        .domain("apps.retro.io")
                        .host(tag + "-todos-webui")
                        .build()))
                .then(cf.applications()
                    .start(StartApplicationRequest.builder()
                        .name(tag + "-todos-webui").build())).subscribe();

        // push edge and manually config UI and API endpoints in edge's ENV
        pushApplication(tag + "-todos-edge",
            Paths.get(jarsFolder, "todos-edge-" + version + ".jar").toFile().toPath(), true)
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

    private void deployPublicApps(String tag, String version) {
        // push api
        pushApplication(tag + "-todos-api",
            Paths.get(jarsFolder, "todos-api-" + version + ".jar").toFile().toPath(), true)
                .then(cf.applications()
                    .start(StartApplicationRequest.builder()
                        .name(tag + "-todos-api").build())).subscribe();

        // push webui
        pushApplication(tag + "-todos-webui",
            Paths.get(jarsFolder, "todos-webui-" + version + ".jar").toFile().toPath(), true)
                .then(cf.applications()
                    .start(StartApplicationRequest.builder()
                        .name(tag + "-todos-webui").build())).subscribe();

        // push edge and manually config UI and API endpoints in edge's ENV
        pushApplication(tag + "-todos-edge",
            Paths.get(jarsFolder, "todos-edge-" + version + ".jar").toFile().toPath(), true)
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

    private void deploySpringCloudApps(String tag, String version, String networking) {
        if("private".equalsIgnoreCase(networking)) {
            deployPrivateSpringCloudApps(tag, version);
        } else {
            deployPublicSpringCloudApps(tag, version);
        }
    }

    private void deployPrivateSpringCloudApps(String tag, String version) {
        pushApplication(tag + "-todos-api",
            Paths.get(jarsFolder, "todos-api-" + version + ".jar").toFile().toPath(), true)
                .then(this.cf.routes()
                    .map(MapRouteRequest.builder()
                        .applicationName(tag + "-todos-api")
                        .domain("apps.internal")
                        .host(tag + "-todos-api")
                        .build()))
                .then(this.cf.routes()
                    .unmap(UnmapRouteRequest.builder()
                        .applicationName(tag + "-todos-api")
                        .domain("apps.retro.io")
                        .host(tag + "-todos-api")
                        .build()))
                .then(this.cf.applications()
                    .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                        .name(tag + "-todos-api")
                        .variableName("TRUST_CERTS")
                        .variableValue(cfApi)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                    .applicationName(tag + "-todos-api")
                    .serviceInstanceName("todos-config")
                    .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                    .applicationName(tag + "-todos-api")
                    .serviceInstanceName("todos-registry")
                    .build()))
                .then(cf.applications()
                    .start(StartApplicationRequest.builder()
                        .name(tag + "-todos-api").build())).subscribe();

        pushApplication(tag + "-todos-webui",
            Paths.get(jarsFolder, "todos-webui-" + version + ".jar").toFile().toPath(), true)
                .then(this.cf.routes()
                    .map(MapRouteRequest.builder()
                        .applicationName(tag + "-todos-webui")
                        .domain("apps.internal")
                        .host(tag + "-todos-webui")
                        .build()))
                .then(this.cf.routes()
                    .unmap(UnmapRouteRequest.builder()
                        .applicationName(tag + "-todos-webui")
                        .domain("apps.retro.io")
                        .host(tag + "-todos-webui")
                        .build()))
                .then(this.cf.applications()
                    .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                        .name(tag + "-todos-webui")
                        .variableName("TRUST_CERTS")
                        .variableValue(cfApi)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-webui")
                        .serviceInstanceName("todos-config")
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-webui")
                        .serviceInstanceName("todos-registry")
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-webui").build())).subscribe();

        pushApplication(tag + "-todos-edge",
            Paths.get(jarsFolder, "todos-edge-" + version + ".jar").toFile().toPath(), true)
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
                    .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                        .name(tag + "-todos-edge")
                        .variableName("TRUST_CERTS")
                        .variableValue(cfApi)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-edge")
                        .serviceInstanceName("todos-config")
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                        .applicationName(tag + "-todos-edge")
                        .serviceInstanceName("todos-registry")
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                                .name(tag + "-todos-edge").build())).subscribe();
    }

    private void deployPublicSpringCloudApps(String tag, String version) {

        pushApplication(tag + "-todos-api",
            Paths.get(jarsFolder, "todos-api-" + version + ".jar").toFile().toPath(), true)
                .then(this.cf.applications()
                    .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                        .name(tag + "-todos-api")
                        .variableName("TRUST_CERTS")
                        .variableValue(cfApi)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                    .applicationName(tag + "-todos-api")
                    .serviceInstanceName("todos-config")
                    .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                    .applicationName(tag + "-todos-api")
                    .serviceInstanceName("todos-registry")
                    .build()))
                .then(cf.applications()
                    .start(StartApplicationRequest.builder()
                        .name(tag + "-todos-api").build())).subscribe();

        pushApplication(tag + "-todos-webui",
            Paths.get(jarsFolder, "todos-webui-" + version + ".jar").toFile().toPath(), true)
                .then(this.cf.applications()
                    .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                        .name(tag + "-todos-webui")
                        .variableName("TRUST_CERTS")
                        .variableValue(cfApi)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                    .applicationName(tag + "-todos-webui")
                    .serviceInstanceName("todos-config")
                    .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                    .applicationName(tag + "-todos-webui")
                    .serviceInstanceName("todos-registry")
                    .build()))
                .then(cf.applications()
                    .start(StartApplicationRequest.builder()
                        .name(tag + "-todos-webui").build())).subscribe();

        pushApplication(tag + "-todos-edge",
            Paths.get(jarsFolder, "todos-edge-" + version + ".jar").toFile().toPath(), true)
                .then(this.cf.applications()
                    .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                        .name(tag + "-todos-edge")
                        .variableName("TRUST_CERTS")
                        .variableValue(cfApi)
                        .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                    .applicationName(tag + "-todos-edge")
                    .serviceInstanceName("todos-config")
                    .build()))
                .then(this.cf.services().bind(BindServiceInstanceRequest.builder()
                    .applicationName(tag + "-todos-edge")
                    .serviceInstanceName("todos-registry")
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

    private Mono<Void> pushApplication(String name, Path application, Boolean noStart) {
        return cf.applications()
            .push(PushApplicationRequest.builder()
                .noStart(noStart)
                .memory(1024)
                .name(name)
                .path(application)
                .build());
    }
}
