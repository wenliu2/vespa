// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RotationName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.BuildJob;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author mortent
 */
public class RoutingPolicyMaintainerTest {

    private final DeploymentTester tester = new DeploymentTester();
    private final Application app1 = tester.createApplication("app1", "tenant1", 1, 1L);
    private final Application app2 = tester.createApplication("app2", "tenant1", 1, 1L);

    private final RoutingPolicyMaintainer maintainer = new RoutingPolicyMaintainer(tester.controller(), Duration.ofHours(12),
                                                                                   new JobControl(new MockCuratorDb()),
                                                                                   tester.controllerTester().nameService(),
                                                                                   tester.controllerTester().curator());
    private final ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
            .environment(Environment.prod)
            .region("us-west-1")
            .region("us-central-1")
            .build();

    @Test
    public void maintains_global_routing_policies() {
        int clustersPerZone = 2;
        tester.deployCompletely(app1, applicationPackage);
        Map<Integer, Set<RotationName>> rotations = Map.of(0, Set.of(RotationName.from("r0")));
        provisionLoadBalancers(app1, clustersPerZone, rotations);

        // Creates alias record for cluster0
        maintainer.maintain();
        Supplier<List<Record>> records1 = () -> tester.controllerTester().nameService().findRecords(Record.Type.ALIAS, RecordName.from("r0--app1--tenant1.global.vespa.oath.cloud"));
        assertEquals(2, records1.get().size());
        assertEquals("c0--app1--tenant1.prod.us-central-1.vespa.oath.cloud.", records1.get().get(0).data().asString());
        assertEquals("c0--app1--tenant1.prod.us-west-1.vespa.oath.cloud.", records1.get().get(1).data().asString());

        // Applications gains a new deployment
        ApplicationPackage updatedApplicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .region("us-west-1")
                .region("us-central-1")
                .region("us-east-3")
                .build();
        tester.deployCompletely(app1, updatedApplicationPackage, BuildJob.defaultBuildNumber + 1);

        // Cluster in new deployment is added to the rotation
        provisionLoadBalancers(app1, 2, rotations);
        maintainer.maintain();
        assertEquals(3, records1.get().size());
        assertEquals("c0--app1--tenant1.prod.us-central-1.vespa.oath.cloud.", records1.get().get(0).data().asString());
        assertEquals("c0--app1--tenant1.prod.us-east-3.vespa.oath.cloud.", records1.get().get(1).data().asString());
        assertEquals("c0--app1--tenant1.prod.us-west-1.vespa.oath.cloud.", records1.get().get(2).data().asString());

        // Another appplication is deployed
        Supplier<List<Record>> records2 = () -> tester.controllerTester().nameService().findRecords(Record.Type.ALIAS, RecordName.from("r0--app2--tenant1.global.vespa.oath.cloud"));
        tester.deployCompletely(app2, applicationPackage);
        provisionLoadBalancers(app2, 1, Map.of(0, Set.of(RotationName.from("r0"))));
        maintainer.maintain();
        assertEquals(2, records2.get().size());
        assertEquals("c0--app2--tenant1.prod.us-central-1.vespa.oath.cloud.", records2.get().get(0).data().asString());
        assertEquals("c0--app2--tenant1.prod.us-west-1.vespa.oath.cloud.", records2.get().get(1).data().asString());

        // Rotation for app1 is removed
        provisionLoadBalancers(app1, clustersPerZone, Collections.emptyMap());
        maintainer.maintain();
        assertEquals(0, records1.get().size());
        assertEquals("Rotations for " + app2 + " are not removed", 2, records2.get().size());
    }

    @Test
    public void maintains_routing_policies_per_zone() {
        // Deploy application
        int clustersPerZone = 2;
        tester.deployCompletely(app1, applicationPackage);
        provisionLoadBalancers(app1, clustersPerZone);

        // Creates records and policies for all clusters in all zones
        maintainer.maintain();
        Set<String> expectedRecords = Set.of(
                "c0--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c0--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, recordNames());
        assertEquals(4, policies(app1).size());

        // Next run does nothing
        maintainer.maintain();
        assertEquals(expectedRecords, recordNames());
        assertEquals(4, policies(app1).size());

        // Add 1 cluster in each zone
        provisionLoadBalancers(app1, clustersPerZone + 1);
        maintainer.maintain();
        expectedRecords = Set.of(
                "c0--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c2--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c0--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c2--app1--tenant1.prod.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, recordNames());
        assertEquals(6, policies(app1).size());

        // Add another application
        tester.deployCompletely(app2, applicationPackage);
        provisionLoadBalancers(app2, clustersPerZone);
        maintainer.maintain();
        expectedRecords = Set.of(
                "c0--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c2--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c0--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c2--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c0--app2--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c1--app2--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c0--app2--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c1--app2--tenant1.prod.us-west-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, recordNames());
        assertEquals(4, policies(app2).size());

        // Remove cluster from app1
        provisionLoadBalancers(app1, clustersPerZone);
        maintainer.maintain();
        expectedRecords = Set.of(
                "c0--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c0--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c0--app2--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c1--app2--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c0--app2--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c1--app2--tenant1.prod.us-west-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, recordNames());

        // Remove app2 completely
        tester.controller().applications().require(app2.id()).deployments().keySet()
              .forEach(zone -> {
                  tester.controller().applications().deactivate(app2.id(), zone);
                  tester.configServer().removeLoadBalancers(app2.id(), zone);
              });
        maintainer.maintain();
        expectedRecords = Set.of(
                "c0--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-west-1.vespa.oath.cloud",
                "c0--app1--tenant1.prod.us-central-1.vespa.oath.cloud",
                "c1--app1--tenant1.prod.us-central-1.vespa.oath.cloud"
        );
        assertEquals(expectedRecords, recordNames());
    }

    private Set<RoutingPolicy> policies(Application application) {
        return tester.controller().curator().readRoutingPolicies(application.id());
    }

    private List<Record> findAlias(String name) {
        return tester.controllerTester().nameService().findRecords(Record.Type.ALIAS, RecordName.from(name));
    }

    private Set<String> recordNames() {
        return tester.controllerTester().nameService().records().stream()
                     .map(Record::name)
                     .map(RecordName::asString)
                     .collect(Collectors.toSet());
    }

    private void provisionLoadBalancers(Application application, int clustersPerZone, Map<Integer, Set<RotationName>> clusterRotations) {
        tester.controller().applications().require(application.id())
              .deployments().keySet()
              .forEach(zone -> tester.configServer().removeLoadBalancers(application.id(), zone));
        tester.controller().applications().require(application.id())
              .deployments().keySet()
              .forEach(zone -> tester.configServer()
                                     .addLoadBalancers(zone, createLoadBalancers(zone, application.id(), clustersPerZone, clusterRotations)));
    }

    private void provisionLoadBalancers(Application application, int clustersPerZone) {
        provisionLoadBalancers(application, clustersPerZone, Collections.emptyMap());
    }

    private static List<LoadBalancer> createLoadBalancers(ZoneId zone, ApplicationId application, int count,
                                                   Map<Integer, Set<RotationName>> clusterRotations) {
        List<LoadBalancer> loadBalancers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Set<RotationName> rotations = clusterRotations.getOrDefault(i, Collections.emptySet());
            loadBalancers.add(
                    new LoadBalancer("LB-" + i + "-Z-" + zone.value(),
                                     application,
                                     ClusterSpec.Id.from("c" + i),
                                     HostName.from("loadbalancer-" + i + "-" + application.serializedForm() +
                                                   "-zone-" + zone.value()),
                                     Optional.of("dns-zone-1"),
                                     rotations));
        }
        return loadBalancers;
    }

}
