package io.retro.cf.cfclient;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.operations.DefaultCloudFoundryOperations;
import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.TokenProvider;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.doppler.ReactorDopplerClient;
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider;
import org.cloudfoundry.uaa.UaaClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Context {

    @Bean
    DefaultConnectionContext connectionContext(
        @Value("${cf.api}") String api,
        @Value("${cf.skipSslValidation}") Boolean skipSslValidation) {
        return DefaultConnectionContext.builder()
            .apiHost(api).skipSslValidation(skipSslValidation)
                .build();
    }

    @Bean
    PasswordGrantTokenProvider tokenProvider(
        @Value("${cf.username}") String username,
        @Value("${cf.password}") String password) {
        return PasswordGrantTokenProvider.builder()
            .password(password)
                .username(username)
                    .build();
    }

    @Bean
    ReactorCloudFoundryClient cloudFoundryClient(
        ConnectionContext connectionContext,
        TokenProvider tokenProvider) {
        return ReactorCloudFoundryClient.builder()
            .connectionContext(connectionContext)
                .tokenProvider(tokenProvider).build();
    }

    @Bean
    DefaultCloudFoundryOperations cloudFoundryOperations(
            CloudFoundryClient cloudFoundryClient,
            @Value("${cf.organization}") String organization,
            @Value("${cf.space}") String space) {
        return DefaultCloudFoundryOperations.builder()
            .cloudFoundryClient(cloudFoundryClient)
                .organization(organization)
                    .space(space).build();
    }
}
