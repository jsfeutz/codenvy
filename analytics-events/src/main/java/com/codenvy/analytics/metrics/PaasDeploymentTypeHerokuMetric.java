/*
 *    Copyright (C) 2013 Codenvy.
 *
 */
package com.codenvy.analytics.metrics;

import java.io.IOException;

/**
 * @author <a href="mailto:abazko@codenvy.com">Anatoliy Bazko</a>
 */
public class PaasDeploymentTypeHerokuMetric extends ValueFromMapMetric {

    PaasDeploymentTypeHerokuMetric() throws IOException {
        super(MetricType.PAAS_DEPLOYEMNT_TYPE_HEROKU, MetricFactory.createMetric(MetricType.PAAS_DEPLOYEMNT_TYPES), "Heroku",
              true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTitle() {
        return "% Heroku";
    }
}
