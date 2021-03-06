// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author mpolden
 */
public class LoadBalancerServiceMock implements LoadBalancerService {

    private final Map<LoadBalancerId, LoadBalancer> loadBalancers = new HashMap<>();

    public Map<LoadBalancerId, LoadBalancer> loadBalancers() {
        return Collections.unmodifiableMap(loadBalancers);
    }

    @Override
    public Protocol protocol() {
        return Protocol.ipv4;
    }

    @Override
    public LoadBalancer create(ApplicationId application, ClusterSpec.Id cluster, Set<Real> reals) {
        LoadBalancer loadBalancer = new LoadBalancer(
                new LoadBalancerId(application, cluster),
                HostName.from("lb-" + application.toShortString() + "-" + cluster.value()),
                Optional.of(new DnsZone("zone-id-1")),
                Collections.singleton(4443),
                ImmutableSet.of("10.2.3.0/24", "10.4.5.0/24"),
                reals,
                false);
        loadBalancers.put(loadBalancer.id(), loadBalancer);
        return loadBalancer;
    }

    @Override
    public void remove(LoadBalancerId loadBalancer) {
        loadBalancers.remove(loadBalancer);
    }

}
