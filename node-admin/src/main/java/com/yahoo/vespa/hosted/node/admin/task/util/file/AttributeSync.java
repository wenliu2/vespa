// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Class to converge file/directory attributes like owner and permissions to wanted values.
 * Typically used by higher abstraction layers working on files (FileSync/FileWriter) or
 * directories (MakeDirectory).
 *
 * @author hakonhall
 */
public class AttributeSync {
    private static final Logger logger = Logger.getLogger(AttributeSync.class.getName());

    private final UnixPath path;

    private Optional<String> owner = Optional.empty();
    private Optional<String> group = Optional.empty();
    private Optional<String> permissions = Optional.empty();
    private Runnable preModificationCallback = () -> {};

    public AttributeSync(UnixPath path) {
        this.path = path;
    }

    public Optional<String> getPermissions() {
        return permissions;
    }

    public AttributeSync withPermissions(String permissions) {
        this.permissions = Optional.of(permissions);
        return this;
    }

    public Optional<String> getOwner() {
        return owner;
    }

    public AttributeSync withOwner(String owner) {
        this.owner = Optional.of(owner);
        return this;
    }

    public Optional<String> getGroup() {
        return group;
    }

    public AttributeSync withGroup(String group) {
        this.group = Optional.of(group);
        return this;
    }

    public AttributeSync withPreModificationCallback(Runnable callback) {
        this.preModificationCallback = new RunnableOnce(callback);
        return this;
    }

    public AttributeSync with(PartialFileData fileData) {
        owner = fileData.getOwner();
        group = fileData.getGroup();
        permissions = fileData.getPermissions();
        return this;
    }

    public boolean converge(TaskContext context) {
        return converge(context, new FileAttributesCache(path));
    }

    /**
     * Path must exist before calling converge.
     */
    public boolean converge(TaskContext context, FileAttributesCache currentAttributes) {
        RunnableOnce callback = new RunnableOnce(preModificationCallback);
        boolean systemModified = updateAttribute(
                context,
                callback,
                "owner",
                owner,
                () -> currentAttributes.get().owner(),
                path::setOwner);

        systemModified |= updateAttribute(
                context,
                callback,
                "group",
                group,
                () -> currentAttributes.get().group(),
                path::setGroup);

        systemModified |= updateAttribute(
                context,
                callback,
                "permissions",
                permissions,
                () -> currentAttributes.get().permissions(),
                path::setPermissions);

        return systemModified;
    }

    private boolean updateAttribute(TaskContext context,
                                    RunnableOnce callback,
                                    String attributeName,
                                    Optional<String> wantedValue,
                                    Supplier<String> currentValueSupplier,
                                    Consumer<String> valueSetter) {
        if (!wantedValue.isPresent()) {
            return false;
        }

        String currentValue = currentValueSupplier.get();
        if (Objects.equals(currentValue, wantedValue.get())) {
            return false;
        }

        preModificationCallback.run();
        context.recordSystemModification(
                logger,
                String.format("Changing %s of %s from %s to %s",
                        attributeName,
                        path.toString(),
                        currentValue,
                        wantedValue.get()));

        valueSetter.accept(wantedValue.get());

        return true;
    }

    private class RunnableOnce implements Runnable {
        private final Runnable runnable;
        private boolean hasRun = false;

        private RunnableOnce(Runnable runnable) {
            this.runnable = runnable;
        }

        public void run() {
            if (hasRun) return;
            hasRun = true;
            runnable.run();
        }

        public void reset() {
            hasRun = false;
        }
    }
}
