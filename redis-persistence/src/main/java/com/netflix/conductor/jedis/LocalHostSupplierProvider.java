package com.netflix.conductor.jedis;

import javax.inject.Inject;
import javax.inject.Provider;

import com.google.common.collect.Lists;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostBuilder;
import com.netflix.dyno.connectionpool.HostSupplier;

public class LocalHostSupplierProvider implements Provider<HostSupplier> {
    private final Configuration configuration;

    @Inject
    public LocalHostSupplierProvider(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public HostSupplier get() {
        Host dynoHost = new HostBuilder()
                .setHostname("localhost")
                .setIpAddress("0")
                .setRack(configuration.getAvailabilityZone())
                .setStatus(Host.Status.Up)
                .createHost();
        return ()-> Lists.newArrayList(dynoHost);
    }
}
