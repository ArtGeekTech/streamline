/**
  * Copyright 2017 Hortonworks.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at

  *   http://www.apache.org/licenses/LICENSE-2.0

  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
 **/
package com.hortonworks.streamline.streams.cluster.service.metadata.common;

import com.hortonworks.streamline.streams.catalog.ServiceConfiguration;
import com.hortonworks.streamline.streams.catalog.exception.ServiceConfigurationNotFoundException;
import com.hortonworks.streamline.streams.catalog.exception.ServiceNotFoundException;
import com.hortonworks.streamline.streams.cluster.discovery.ambari.ServiceConfigurations;
import com.hortonworks.streamline.streams.cluster.service.EnvironmentService;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class OverrideHadoopConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(OverrideHadoopConfiguration.class);

    public static <T extends Configuration> T override(EnvironmentService environmentService, Long clusterId,
                                                       ServiceConfigurations service, List<String> configNames, T configuration)
            throws IOException, ServiceConfigurationNotFoundException, ServiceNotFoundException {
        return override(environmentService, clusterId, service, configNames, configuration, Collections.emptyMap());
    }

    public static <T extends Configuration> T override(EnvironmentService environmentService, Long clusterId,
            ServiceConfigurations service, List<String> configNames, T configuration, Map<String, Function<String, String>> samOverrides)
                throws IOException, ServiceConfigurationNotFoundException, ServiceNotFoundException {

        for (String configName : configNames) {
            final ServiceConfiguration serviceConfig = environmentService.getServiceConfigurationByName(
                    getServiceIdByClusterId(environmentService, clusterId, service), configName);

            if (serviceConfig != null) {
                final Map<String, String> configurationMap = serviceConfig.getConfigurationMap();
                if (configurationMap != null) {
                    for (Map.Entry<String, String> propKeyVal : configurationMap.entrySet()) {
                        LOG.debug("Overriding property {}", propKeyVal);
                        final String key = propKeyVal.getKey();
                        final String val = samOverrides.containsKey(key)
                            ? getSamOverride(samOverrides, propKeyVal)
                            : propKeyVal.getValue();

                        if (key != null && val != null) {
                            configuration.set(key, val);
                            LOG.debug("Set property {}={}", key, val);
                        } else {
                            LOG.warn("Skipping null key/val property {}", propKeyVal);
                        }
                    }
                }
            } else {
                throw new ServiceConfigurationNotFoundException("Required [" + configName +
                        "] configuration not found for service [" + service.name() + "]");
            }
        }
        return configuration;
    }

    private static String getSamOverride(Map<String, Function<String, String>> samOverrides, Map.Entry<String, String> propKeyVal) {
        final String samOverride = samOverrides.get(propKeyVal.getKey()).apply(propKeyVal.getValue());
        LOG.debug("Streaming Analytics Manager property override {}={}", propKeyVal.getKey(), samOverride);
        return samOverride;
    }

    private static Long getServiceIdByClusterId(EnvironmentService environmentService, Long clusterId,
            ServiceConfigurations service) throws ServiceNotFoundException {

        final Long serviceId = environmentService.getServiceIdByName(clusterId, service.name());
        if (serviceId == null) {
            throw new ServiceNotFoundException(clusterId, service.name());
        }
        return serviceId;
    }
}
