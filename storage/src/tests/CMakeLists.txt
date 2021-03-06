# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Runner for unit tests written in gtest.
# NOTE: All new test classes should be added here.
vespa_add_executable(storage_gtest_runner_app TEST
    SOURCES
    gtest_runner.cpp
    DEPENDS
    storage_testbucketmover
)

vespa_add_test(
    NAME storage_gtest_runner_app
    COMMAND storage_gtest_runner_app
    DEPENDS storage_gtest_runner_app
)

# Runner for unit tests written in CppUnit (DEPRECATED).
vespa_add_executable(storage_testrunner_app TEST
    SOURCES
    testrunner.cpp
    DEPENDS
    storage_teststorageserver
    storage_testvisiting
    storage_testbucketdb
    storage_testcommon
    storage_testhostreporter
    storage_testdistributor
    storage_testpersistence
    storage_testfilestorage
    storage_teststatus
)

# TODO: Test with a larger chunk size to parallelize test suite runs 
vespa_add_test(
    NAME storage_testrunner_app
    COMMAND python ${PROJECT_SOURCE_DIR}/cppunit-parallelize.py --chunks 8 $<TARGET_FILE:storage_testrunner_app>
    DEPENDS storage_testrunner_app
)
