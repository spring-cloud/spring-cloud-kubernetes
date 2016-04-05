package org.springframework.cloud.commons.util;

import lombok.Data;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 * Copied from spring-cloud-commons as a workaround for version incompatibilities
 */
public class InetUtils {

    public static int getIpAddressAsInt(String host) {
        return new HostInfo(host).getIpAddressAsInt();
    }

    @Data
    public static final class HostInfo {
        public boolean override;
        private String ipAddress;
        private String hostname;

        HostInfo(String hostname) {
            this.hostname = hostname;
        }

        HostInfo() {
        }

        public int getIpAddressAsInt() {
            InetAddress inetAddress = null;
            String host = this.ipAddress;
            if (host == null) {
                host = this.hostname;
            }
            try {
                inetAddress = InetAddress.getByName(host);
            } catch (final UnknownHostException e) {
                throw new IllegalArgumentException(e);
            }
            return ByteBuffer.wrap(inetAddress.getAddress()).getInt();
        }
    }
}