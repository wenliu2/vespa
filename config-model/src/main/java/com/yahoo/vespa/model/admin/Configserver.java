// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.AbstractService;

/**
 * Represents a Configserver. There may be one or more Configservers in a
 * Vespa system.
 *
 * NOTE: The Configserver is not started by the config system, and
 * does not receive any config. It's included here so we know what host
 * it runs on, and to give an error message if another service tries
 * to reserve the ports it is using.
 *
 * @author  gjoranv
 */
public class Configserver extends AbstractService {
    private static final long serialVersionUID = 1L;
    public static final int defaultRpcPort = 19070;

    private final int rpcPort;

    public Configserver(AbstractConfigProducer parent, String name, int rpcPort) {
        super(parent, name);
        this.rpcPort = rpcPort;
        portsMeta.on(0).tag("rpc").tag("config");
        portsMeta.on(1).tag("http").tag("config").tag("state");
        setProp("clustertype", "admin");
        setProp("clustername", "admin");
    }

    /**
     * Returns the desired base port for this service.
     */
    public int getWantedPort() {
        return rpcPort;
    }

    /**
     * The desired base port is the only allowed base port.
     * @return 'true' always
     */
    public boolean requiresWantedPort() {
        return getId() < 2;
    }

     /**
     * @return the number of ports needed by the configserver.
     */
    public int getPortCount() { return 2; }

    @Override
    public String[] getPortSuffixes() {
        String[] suffixes = new String[2];
        suffixes[0] = "rpc";
        suffixes[1] = "http";
        return suffixes;
    }

    /**
     * The configserver is not started by the config system!
     */
    public boolean getAutostartFlag()   { return false; }

    /**
     * The configserver is not started by the config system!
     */
    public boolean getAutorestartFlag() { return false; }

    private int getConfigServerRpcPort() {
        return getRelativePort(0);
    }

    private int getConfigServerHttpPort() {
        return getRelativePort(1);
    }

    ConfigServerSpec getConfigServerSpec() {
        return new Spec(getHostName(), getConfigServerRpcPort(), getConfigServerHttpPort(), ZooKeepersConfigProvider.zkPort);
    }

    @Override
    public int getHealthPort()  {
        return getRelativePort(1);
    }

    // TODO: Remove this implementation when we are on Hosted Vespa.
    public static class Spec implements ConfigServerSpec {

        private final String hostName;
        private final int configServerPort;
        private final int httpPort;
        private final int zooKeeperPort;

        public String getHostName() {
            return hostName;
        }

        public int getConfigServerPort() {
            return configServerPort;
        }

        public int getHttpPort() {
            return httpPort;
        }

        public int getZooKeeperPort() {
            return zooKeeperPort;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof ConfigServerSpec) {
                ConfigServerSpec other = (ConfigServerSpec)o;

                return hostName.equals(other.getHostName()) &&
                        configServerPort == other.getConfigServerPort() &&
                        httpPort == other.getHttpPort() &&
                        zooKeeperPort == other.getZooKeeperPort();
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return hostName.hashCode();
        }

        public Spec(String hostName, int configServerPort, int httpPort, int zooKeeperPort) {
            this.hostName = hostName;
            this.configServerPort = configServerPort;
            this.httpPort = httpPort;
            this.zooKeeperPort = zooKeeperPort;
        }
    }

}
