package io.retro.cf.cfclient;

import org.cloudfoundry.client.v3.applications.Application;
import org.cloudfoundry.client.v3.spaces.Space;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.PushApplicationRequest;
import org.cloudfoundry.operations.applications.SetEnvironmentVariableApplicationRequest;
import org.cloudfoundry.operations.applications.StartApplicationRequest;
import org.cloudfoundry.operations.organizations.OrganizationSummary;
import org.cloudfoundry.operations.services.ServiceInstanceSummary;
import org.cloudfoundry.operations.spaces.SpaceSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ShellComponent
public class ShellCommands {

    // CF operations API
    private CloudFoundryOperations cf;

    // autowire operations instance
    public ShellCommands(@Autowired CloudFoundryOperations operations) {
        this.cf = operations;
    }

    /**
     * Programmatically push 3 applications to PAS
     * @throws IOException
     */
    @ShellMethod("push")
    public void push(@ShellOption(help = "tag prefix for app hostname", defaultValue = "") String tag) throws IOException {
        if(tag.length() < 1) {
            tag = UUID.randomUUID().toString().substring(0,8);
        }
        Map<String, String> envVars = new HashMap<>();
        envVars.put("TODOS_UI_ENDPOINT", "http://" + tag + "-todos-webui.apps.retro.io");
        envVars.put("TODOS_API_ENDPOINT", "http://" + tag + "-todos-api.apps.retro.io");
        envVars.put("EUREKA_CLIENT_ENABLED", "false");
        envVars.put("SPRING_CLOUD_CONFIG_ENABLED", "false");

        pushApplication(tag + "-todos-api",
            new ClassPathResource("todos-api-1.0.0.SNAP.jar").getFile().toPath(), true)
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

        pushApplication(tag + "-todos-webui",
            new ClassPathResource("todos-webui-1.0.0.SNAP.jar").getFile().toPath(), true)
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

        pushApplication(tag + "-todos-edge",
            new ClassPathResource("todos-edge-1.0.0.SNAP.jar").getFile().toPath(), true)
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
                        .variableValue("http://" + tag + "-todos-webui.apps.retro.io")
                        .build()))
                .then(this.cf.applications()
                    .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                        .name(tag + "-todos-edge")
                        .variableName("TODOS_API_ENDPOINT")
                        .variableValue("http://" + tag + "-todos-api.apps.retro.io")
                        .build()))
                .then(cf.applications()
                    .start(StartApplicationRequest.builder()
                        .name(tag + "-todos-edge").build())).block();
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
