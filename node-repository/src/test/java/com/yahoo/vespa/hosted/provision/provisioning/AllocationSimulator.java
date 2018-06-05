// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;

import javax.swing.JFrame;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Graphically run allocation procedure to ease manual comprehension.
 *
 * Extremely useful when trying to understand test cases and build corner cases.
 */
public class AllocationSimulator {

    private final MockNameResolver nameResolver;
    private AllocationVisualizer visualizer;
    private NodeList nodes;
    private NodeFlavors flavors;
    private final NodeRepository nodeRepository;
    private final NodeRepositoryProvisioner nodeRepositoryProvisioner;

    private AllocationSimulator(AllocationVisualizer visualizer) {
        this.visualizer = visualizer;

        //
        // Setup flavors
        //
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("host-large", 8., 8., 8, Flavor.Type.BARE_METAL).idealHeadroom(1);
        b.addFlavor("host-small", 5., 5., 5, Flavor.Type.BARE_METAL).idealHeadroom(2);
        b.addFlavor("d-1", 1, 1., 1, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-2", 2, 2., 2, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3", 3, 3., 3, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3-disk", 3, 3., 5, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3-mem", 3, 5., 3, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3-cpu", 5, 3., 3, Flavor.Type.DOCKER_CONTAINER);
        flavors = new NodeFlavors(b.build());

        //
        // Initiate nodes in system
        //
        List<Node> initialNodes = new ArrayList<>();
        initialNodes.add(host("host1", flavors.getFlavorOrThrow("host-large")));
        initialNodes.add(host("host2", flavors.getFlavorOrThrow("host-large")));
        initialNodes.add(host("host3", flavors.getFlavorOrThrow("host-large")));
        initialNodes.add(host("host4", flavors.getFlavorOrThrow("host-large")));
        initialNodes.add(host("host5", flavors.getFlavorOrThrow("host-large")));
        initialNodes.add(host("host6", flavors.getFlavorOrThrow("host-large")));
        initialNodes.add(host("host7", flavors.getFlavorOrThrow("host-small")));
        initialNodes.add(host("host8", flavors.getFlavorOrThrow("host-small")));
        initialNodes.add(host("host9", flavors.getFlavorOrThrow("host-small")));
        initialNodes.add(host("host10", flavors.getFlavorOrThrow("host-small")));

        nodes = new NodeList(initialNodes);

        nameResolver = new MockNameResolver();
        nameResolver.mockAnyLookup();
        nodeRepository = new NodeRepository(flavors, new MockCurator(), Clock.fixed(Instant.EPOCH, ZoneId.systemDefault()), Zone.defaultZone(), nameResolver, new DockerImage("foo"), true);

        nodeRepository.addNodes(nodes.asList());

        nodeRepository.setDirty(nodes.asList(), Agent.system, "eur");
        nodeRepository.setReady(nodeRepository.getNodes(), Agent.system, "eka");

        NestedTransaction transaction = new NestedTransaction();
        nodeRepository.activate(nodeRepository.getNodes(), transaction);
        transaction.commit();

        nodeRepositoryProvisioner = new NodeRepositoryProvisioner(nodeRepository, flavors, Zone.defaultZone());

        visualizer.addStep(nodeRepository.getNodes(), "Initial state", "");
    }

    /* ------------ Create node and flavor methods ----------------*/

    private Node host(String hostname, Flavor flavor) {
        return node(hostname, flavor, Optional.empty(), Optional.empty());
    }

    private String randomIpv4Address() {
        Random random = new Random();
        return "10." + random.nextInt(254) + "." + random.nextInt(254) + "." + random.nextInt(254);
    }

    private String randomIp6Address() {
        Random random = new Random();
        return "::"+Integer.toHexString(random.nextInt(65534)) + ":" + Integer.toHexString(random.nextInt(65534));
    }

    private Node node(String hostname, Flavor flavor, Optional<String> parent, Optional<String> tenant) {
        return new Node("fake", Collections.singleton(randomIpv4Address()),
                parent.isPresent() ? Collections.emptySet() : getAdditionalIPs(), hostname, parent, flavor, Status.initial(),
                parent.isPresent() ? Node.State.ready : Node.State.provisioned, allocation(tenant), History.empty(), parent.isPresent() ? NodeType.tenant : NodeType.host);
    }

    private Set<String> getAdditionalIPs() {
        Set<String> h = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            h.add(randomIp6Address());
        }
        return h;
    }

    private Optional<Allocation> allocation(Optional<String> tenant) {
        if (tenant.isPresent()) {
            Allocation allocation = new Allocation(app(tenant.get()),
                                                   ClusterMembership.from("container/id/1/3", new Version()),
                                                   Generation.inital(),
                                                   false);
            return Optional.of(allocation);
        }
        return Optional.empty();
    }

    private ApplicationId app(String tenant) {
        return new ApplicationId.Builder()
                .tenant(tenant)
                .applicationName("test")
                .instanceName("default").build();
    }

    private ClusterSpec cluster() {
        return ClusterSpec.from(ClusterSpec.Type.container, ClusterSpec.Id.from("test"), ClusterSpec.Group.from(1), Version.fromString("6.41"), false);
    }

    /* ------------ Methods to add events to the system ----------------*/

    public void addCluster(String task, int count, Flavor flavor, String id) {
        NodeSpec.CountNodeSpec nodeSpec = new NodeSpec.CountNodeSpec(count, flavor, false);
        nodes = new NodeList(nodes.asList());

        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Version.fromString("6.42"), false);

        List<HostSpec> prepare = nodeRepositoryProvisioner.prepare(ApplicationId.fromSerializedForm("tenant:" + id + ":instance"), cluster, Capacity.fromNodeCount(count, flavor.name()), 1, (level, message) -> System.out.println(level + ": " + message));

        visualizer.addStep(nodeRepository.getNodes(), task, "prepare");

        nodeRepositoryProvisioner.activate(new NestedTransaction(), ApplicationId.fromSerializedForm("tenant:" + id + ":instance"), prepare);
    }


    public static void main(String[] args) {

        AllocationVisualizer visualizer = new AllocationVisualizer();

        javax.swing.SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Allocation Simulator");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(visualizer);
            frame.pack();
            frame.setVisible(true);
        });

        AllocationSimulator simulator = new AllocationSimulator(visualizer);
        simulator.addCluster("App1 : 3 * d-1 nodes", 3, simulator.flavors.getFlavorOrThrow("d-1"), "App1");
        simulator.addCluster("App2 : 2 * d-2 nodes", 2, simulator.flavors.getFlavorOrThrow("d-2"), "App2");
        simulator.addCluster("App3 : 3 * d-2 nodes", 3, simulator.flavors.getFlavorOrThrow("d-2"), "App3");
        simulator.addCluster("App4 : 3 * d-3 nodes", 3, simulator.flavors.getFlavorOrThrow("d-3"), "App4");
        simulator.addCluster("App5 : 3 * d-3 nodes", 3, simulator.flavors.getFlavorOrThrow("d-3"), "App5");
        simulator.addCluster("App6 : 5 * d-2 nodes", 5, simulator.flavors.getFlavorOrThrow("d-2"), "App6");
    }

}
