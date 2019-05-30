package io.retro.cf.cfclient;

import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.PushApplicationRequest;
import org.cloudfoundry.operations.applications.SetEnvironmentVariableApplicationRequest;
import org.cloudfoundry.operations.applications.StartApplicationRequest;
import org.cloudfoundry.operations.organizations.OrganizationSummary;
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
    // cf domain
    @Value("${cf.domain}") String cfDomain;
    // CF operations API
    private CloudFoundryOperations cf;

    // autowire operations instance
    public ShellCommands(@Autowired CloudFoundryOperations operations) {
        this.cf = operations;
    }

    /**
     * Programmatically push 3 applications to PAS non SCS version
     */
    @ShellMethod("cf push")
    public void push(@ShellOption(help = "tag prefix for app hostname", defaultValue = "") String tag,
        @ShellOption(help = "version (ex: 1.0.0.RELEASE, 1.0.0.SNAP", defaultValue = "1.0.0.SNAP") String version) {
        if(tag.length() < 1) {
            tag = UUID.randomUUID().toString().substring(0,8);
        }

        // push api
        pushApplication(tag + "-todos-api",
            Paths.get(jarsFolder, "todos-api-" + version + ".jar").toFile().toPath(), true)
                .then(this.cf.applications()
                    .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                        .name(tag + "-todos-api")
                        .variableName("EUREKA_CLIENT_ENABLED")
                        .variableValue("false")
                        .build()))
                .then(this.cf.applications()
                    .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                        .name(tag + "-todos-api")
                        .variableName("SPRING_CLOUD_CONFIG_ENABLED")
                        .variableValue("false")
                        .build()))
                .then(cf.applications()
                    .start(StartApplicationRequest.builder()
                        .name(tag + "-todos-api").build())).block();
        // push webui
        pushApplication(tag + "-todos-webui",
            Paths.get(jarsFolder, "todos-webui-" + version + ".jar").toFile().toPath(), true)
                .then(this.cf.applications()
                    .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                        .name(tag + "-todos-webui")
                        .variableName("EUREKA_CLIENT_ENABLED")
                        .variableValue("false")
                        .build()))
                .then(this.cf.applications()
                    .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                        .name(tag + "-todos-webui")
                        .variableName("SPRING_CLOUD_CONFIG_ENABLED")
                        .variableValue("false")
                        .build()))
                .then(cf.applications()
                        .start(StartApplicationRequest.builder()
                            .name(tag + "-todos-webui").build())).block();
        // push edge
        pushApplication(tag + "-todos-edge",
            Paths.get(jarsFolder, "todos-edge-" + version + ".jar").toFile().toPath(), true)
                .then(this.cf.applications()
                    .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                        .name(tag + "-todos-edge")
                        .variableName("EUREKA_CLIENT_ENABLED")
                        .variableValue("false")
                        .build()))
                .then(this.cf.applications()
                    .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                        .name(tag + "-todos-edge")
                        .variableName("SPRING_CLOUD_CONFIG_ENABLED")
                        .variableValue("false")
                        .build()))
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
                        .name(tag + "-todos-edge").build())).block();
    }

    @ShellMethod("cf push with scs")
    public void pushscs(@ShellOption(help = "tag prefix for app hostname", defaultValue = "") String tag,
        @ShellOption(help = "version (ex: 1.0.0.RELEASE, 1.0.0.SNAP", defaultValue = "1.0.0.SNAP") String version) {
        if(tag.length() < 1) {
            tag = UUID.randomUUID().toString().substring(0,8);
        }

        pushApplication(tag + "-todos-api",
            Paths.get(jarsFolder, "todos-api-" + version + ".jar").toFile().toPath(), true)
            .then(cf.applications()
                .start(StartApplicationRequest.builder()
                    .name(tag + "-todos-api").build())).block();

        pushApplication(tag + "-todos-webui",
            Paths.get(jarsFolder, "todos-webui-" + version + ".jar").toFile().toPath(), true)
            .then(cf.applications()
                .start(StartApplicationRequest.builder()
                    .name(tag + "-todos-webui").build())).block();

        pushApplication(tag + "-todos-edge",
            Paths.get(jarsFolder, "todos-edge-" + version + ".jar").toFile().toPath(), true)
            .then(cf.applications()
                .start(StartApplicationRequest.builder()
                    .name(tag + "-todos-edge").build())).block();

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
