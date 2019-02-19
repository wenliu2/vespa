// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;


import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * An in-memory name service for testing purposes.
 *
 * @author mpolden
 */
public class MemoryNameService implements NameService {

    private final Set<Record> records = new TreeSet<>();

    public Set<Record> records() {
        return Collections.unmodifiableSet(records);
    }

    @Override
    public Record createCname(RecordName name, RecordData canonicalName) {
        Record record = new Record(Record.Type.CNAME, name, canonicalName);
        records.add(record);
        return record;
    }

    @Override
    public List<Record> createAlias(RecordName name, Set<AliasTarget> targets) {
        List<Record> records = targets.stream()
                                     .sorted((a, b) -> Comparator.comparing(AliasTarget::name).compare(a, b))
                                     .map(target -> new Record(Record.Type.ALIAS, name,
                                                               RecordData.fqdn(target.name().value())))
                                     .collect(Collectors.toList());
        // Satisfy idempotency contract of interface
        removeRecords(findRecords(Record.Type.ALIAS, name));
        this.records.addAll(records);
        return records;
    }

    @Override
    public List<Record> findRecords(Record.Type type, RecordName name) {
        return records.stream()
                      .filter(record -> record.type() == type && record.name().equals(name))
                      .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<Record> findRecords(Record.Type type, RecordData data) {
        return records.stream()
                      .filter(record -> record.type() == type && record.data().equals(data))
                      .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public void updateRecord(Record record, RecordData newData) {
        List<Record> records = findRecords(record.type(), record.name());
        if (records.isEmpty()) {
            throw new IllegalArgumentException("No record with data '" + newData.asString() + "' exists");
        }
        if (records.size() > 1) {
            throw new IllegalArgumentException("Cannot update multi-value record '" + record.name().asString() +
                                               "' with '" + newData.asString() + "'");
        }
        Record existing = records.get(0);
        this.records.remove(existing);
        this.records.add(new Record(existing.type(), existing.name(), newData));
    }

    @Override
    public void removeRecords(List<Record> records) {
        this.records.removeAll(records);
    }

}
