// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerList;
import com.yahoo.vespa.hosted.provision.node.filter.ApplicationFilter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

/**
 * @author mpolden
 */
public class LoadBalancersResponse extends HttpResponse {

    private final NodeRepository nodeRepository;
    private final HttpRequest request;

    public LoadBalancersResponse(HttpRequest request, NodeRepository nodeRepository) {
        super(200);
        this.request = request;
        this.nodeRepository = nodeRepository;
    }

    private Optional<ApplicationId> application() {
        return Optional.ofNullable(request.getProperty("application"))
                       .map(ApplicationFilter::toApplicationId);
    }

    private List<LoadBalancer> loadBalancers() {
        LoadBalancerList loadBalancers = nodeRepository.loadBalancers();
        return application().map(loadBalancers::owner)
                            .map(LoadBalancerList::asList)
                            .orElseGet(loadBalancers::asList);
    }

    @Override
    public String getContentType() { return "application/json"; }

    @Override
    public void render(OutputStream stream) throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor loadBalancerArray = root.setArray("loadBalancers");

        loadBalancers().forEach(lb -> {
            Cursor lbObject = loadBalancerArray.addObject();
            lbObject.setString("id", lb.id().serializedForm());
            lbObject.setString("application", lb.id().application().application().value());
            lbObject.setString("tenant", lb.id().application().tenant().value());
            lbObject.setString("instance", lb.id().application().instance().value());
            lbObject.setString("cluster", lb.id().cluster().value());
            lbObject.setString("hostname", lb.hostname().value());
            lb.dnsZone().ifPresent(dnsZone -> lbObject.setString("dnsZone", dnsZone.id()));

            Cursor networkArray = lbObject.setArray("networks");
            lb.networks().forEach(networkArray::addString);

            Cursor portArray = lbObject.setArray("ports");
            lb.ports().forEach(portArray::addLong);

            Cursor realArray = lbObject.setArray("reals");
            lb.reals().forEach(real -> {
                Cursor realObject = realArray.addObject();
                realObject.setString("hostname", real.hostname().value());
                realObject.setString("ipAddress", real.ipAddress());
                realObject.setLong("port", real.port());
            });

            Cursor rotationArray = lbObject.setArray("rotations");
            lb.rotations().forEach(rotation -> {
                Cursor rotationObject = rotationArray.addObject();
                rotationObject.setString("name", rotation.value());
            });

            lbObject.setBool("inactive", lb.inactive());
        });

        new JsonFormat(true).encode(stream, slime);
    }

}
