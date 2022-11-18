package com.bcbsfl.registry;
import com.google.inject.servlet.ServletModule;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.gc.GcLogger;
import com.netflix.spectator.jvm.Jmx;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.MetricsServlet;

// provide a prometheus end point to publish metrics from conductor
public class PrometheusMetricsModule extends ServletModule {

    @Override
    protected void configureServlets() {
    	// Initialize the Registry and add it to the global registry
        Spectator.globalRegistry().add(new DefaultRegistry());
        CollectorRegistry.defaultRegistry.register(new PrometheusCollector());
        // enable collection of jvm  stats
        Jmx.registerStandardMXBeans(Spectator.globalRegistry());
        //enable gc logger
        new GcLogger().start(null);
        serve("/metrics").with(new MetricsServlet());
    }
}
