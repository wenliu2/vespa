// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.OwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Periodically request application ownership confirmation through filing issues.
 *
 * When to file new issues, escalate inactive ones, etc., is handled by the enclosed OwnershipIssues.
 *
 * @author jonmv
 */
public class ApplicationOwnershipConfirmer extends Maintainer {

    private final OwnershipIssues ownershipIssues;

    public ApplicationOwnershipConfirmer(Controller controller, Duration interval, JobControl jobControl, OwnershipIssues ownershipIssues) {
        super(controller, interval, jobControl);
        this.ownershipIssues = ownershipIssues;
    }

    @Override
    protected void maintain() {
        confirmApplicationOwnerships();
        ensureConfirmationResponses();
        updateConfirmedApplicationOwners();
    }

    /** File an ownership issue with the owners of all applications we know about. */
    private void confirmApplicationOwnerships() {
        ApplicationList.from(controller().applications().asList())
                       .withProjectId()
                       .hasProductionDeployment()
                       .asList()
                       .stream()
                       .filter(application -> application.createdAt().isBefore(controller().clock().instant().minus(Duration.ofDays(90))))
                       .forEach(application -> {
                           try {
                               Tenant tenant = tenantOf(application.id());
                               Optional<IssueId> ourIssueId = application.ownershipIssueId();
                               Contact contact = tenant.contact().orElseThrow(RuntimeException::new);
                               User assignee = determineAssignee(tenant, application);
                               ourIssueId = ownershipIssues.confirmOwnership(ourIssueId, application.id(), assignee, contact);
                               ourIssueId.ifPresent(issueId -> store(issueId, application.id()));
                           }
                           catch (RuntimeException e) { // Catch errors due to wrong data in the controller, or issues client timeout.
                               log.log(Level.INFO, "Exception caught when attempting to file an issue for '" + application.id() + "': " + Exceptions.toMessageString(e));
                           }
                       });

    }

    /** Escalate ownership issues which have not been closed before a defined amount of time has passed. */
    private void ensureConfirmationResponses() {
        for (Application application : controller().applications().asList())
            application.ownershipIssueId().ifPresent(issueId -> {
                try {
                    Optional<Contact> contact = Optional.of(application.id())
                            .map(this::tenantOf)
                            .filter(t -> t instanceof AthenzTenant)
                            .flatMap(Tenant::contact);
                     ownershipIssues.ensureResponse(issueId, contact);
                }
                catch (RuntimeException e) {
                    log.log(Level.INFO, "Exception caught when attempting to escalate issue with id '" + issueId + "': " + Exceptions.toMessageString(e));
                }
            });
    }

    private void updateConfirmedApplicationOwners() {
        ApplicationList.from(controller().applications().asList())
                .withProjectId()
                .hasProductionDeployment()
                .asList()
                .stream()
                .filter(application -> application.ownershipIssueId().isPresent())
                .forEach(application -> {
                    IssueId ownershipIssueId = application.ownershipIssueId().get();
                    ownershipIssues.getConfirmedOwner(ownershipIssueId).ifPresent(owner -> {
                        controller().applications().lockIfPresent(application.id(), lockedApplication ->
                                controller().applications().store(lockedApplication.withOwner(owner)));
                    });
                });
    }

    private User determineAssignee(Tenant tenant, Application application) {
        return application.owner().orElse(
                tenant instanceof UserTenant ? userFor(tenant) : null
        );
    }

    private Tenant tenantOf(ApplicationId applicationId) {
        return controller().tenants().tenant(applicationId.tenant())
                .orElseThrow(() -> new IllegalStateException("No tenant found for application " + applicationId));
    }

    protected User userFor(Tenant tenant) {
        return User.from(tenant.name().value().replaceFirst(Tenant.userPrefix, ""));
    }

    protected void store(IssueId issueId, ApplicationId applicationId) {
        controller().applications().lockIfPresent(applicationId, application ->
                controller().applications().store(application.withOwnershipIssueId(issueId)));
    }
}
