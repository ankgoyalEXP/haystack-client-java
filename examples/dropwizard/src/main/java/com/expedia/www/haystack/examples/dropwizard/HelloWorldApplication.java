/*
 * Copyright 2018 Expedia, Inc.
 *
 *       Licensed under the Apache License, Version 2.0 (the "License");
 *       you may not use this file except in compliance with the License.
 *       You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *       Unless required by applicable law or agreed to in writing, software
 *       distributed under the License is distributed on an "AS IS" BASIS,
 *       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *       See the License for the specific language governing permissions and
 *       limitations under the License.
 *
 */
package com.expedia.www.haystack.examples.dropwizard;

import java.util.EnumSet;

import javax.servlet.DispatcherType;

import com.codahale.metrics.MetricRegistry;
import com.expedia.www.haystack.examples.dropwizard.health.TemplateHealthCheck;
import com.expedia.www.haystack.examples.dropwizard.resources.HelloWorldResource;
import com.expedia.www.haystack.examples.dropwizard.resources.UntracedResource;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.opentracing.Tracer;
import io.opentracing.contrib.jaxrs2.server.ServerTracingDynamicFeature;
import io.opentracing.contrib.jaxrs2.server.SpanFinishingFilter;

public class HelloWorldApplication extends Application<HelloWorldConfiguration> {

    public static void main(final String[] args) throws Exception {
        new HelloWorldApplication().run(args);
    }

    @Override
    public String getName() {
        return "hello-world";
    }

    private static class BridgeDropwizardMeterRegistry extends DropwizardMeterRegistry {
        public BridgeDropwizardMeterRegistry(DropwizardConfig config, MetricRegistry registry) {
            super(config, registry, HierarchicalNameMapper.DEFAULT, Clock.SYSTEM);
            this.config().namingConvention(NamingConvention.dot);
        }

        @Override
        protected Double nullGaugeValue() {
            return Double.NaN;
        }
    }

    private static interface SimpleDropwizardConfig extends DropwizardConfig {
        SimpleDropwizardConfig DEFAULT = k -> null;

        @Override
        default String prefix() {
            return "hello-world";
        }
    }

    @Override
    public void initialize(final Bootstrap<HelloWorldConfiguration> bootstrap) {
    }

    @Override
    public void run(final HelloWorldConfiguration configuration,
                    final Environment environment) {
        // register all haystack-client metrics into the built-in registry
        Metrics.addRegistry(new BridgeDropwizardMeterRegistry(SimpleDropwizardConfig.DEFAULT, environment.metrics()));

        Tracer tracer = configuration.getTracer().build();
        final ServerTracingDynamicFeature tracingFeature = new ServerTracingDynamicFeature.Builder(tracer).build();
        environment.jersey().register(tracingFeature);

        environment.servlets().addFilter("SpanFinishingFilter", new SpanFinishingFilter(tracer))
            .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");

        final HelloWorldResource resource = new HelloWorldResource(configuration.getTemplate(),
                                                                   configuration.getDefaultName());

        environment.jersey().register(resource);

        environment.jersey().register(new UntracedResource(configuration.getTemplate(),
                                                           configuration.getDefaultName()));

		final TemplateHealthCheck healthCheck = new TemplateHealthCheck(configuration.getTemplate());
		environment.healthChecks().register("template", healthCheck);
	}

}
