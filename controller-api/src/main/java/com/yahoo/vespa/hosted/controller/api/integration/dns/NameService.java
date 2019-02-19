// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dns;

import java.util.List;
import java.util.Set;

/**
 * A managed DNS service.
 *
 * @author mpolden
 */
public interface NameService {

    /**
     * Create a new CNAME record
     *
     * @param name          The alias to create (lhs of the record)
     * @param canonicalName The canonical name which the alias should point to (rhs of the record). This must be a FQDN.
     * @return The created record
     */
    Record createCname(RecordName name, RecordData canonicalName);

    /**
     * Create a non-standard ALIAS record pointing to given targets. Implementations of this can be expected to be
     * idempotent
     *
     * @param targets Targets that should be resolved by this alias. pointing to given targets.
     * @return The created records. One for each target.
     */
    List<Record> createAlias(RecordName name, Set<AliasTarget> targets);

    /** Find all records matching given type and name */
    List<Record> findRecords(Record.Type type, RecordName name);

    /** Find all records matching given type and data */
    List<Record> findRecords(Record.Type type, RecordData data);

    /** Update existing record */
    void updateRecord(Record record, RecordData newData);

    /** Remove given record(s) */
    void removeRecords(List<Record> record);

}
