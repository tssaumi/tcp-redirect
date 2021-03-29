package com.tssaumi.tcpredirect;

import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import static com.tssaumi.tcpredirect.Constants.CLIENT_PROP_FILE;

@Slf4j
public class TCPRedirect extends TCPRedirectServer {

    public TCPRedirect(Properties props) throws Exception {
        super(props);
    }

    protected void loadProperties() throws Exception {
        Set<String> names = props.stringPropertyNames();
        for (String name : names) {
            if (name.matches("channel\\.[0-9A-Z_\\.-]+")) {
                String channel = name.substring(8);
                String hostPort = props.getProperty(name);
                if(hostPort == null || hostPort.isEmpty()) {
                    throw new Exception("Invalid target! "+channel+"="+hostPort);
                }

                int index = hostPort.indexOf(":");
                if(index >= 0) {
                    String host = hostPort.substring(0,index);
                    int port = Integer.parseInt(hostPort.substring(index+1));
                    RedirectTarget target = new RedirectTarget();
                    target.channel = channel;
                    target.host = host;
                    target.port = port;
                    channelMap.put(channel, target);
                }
            }
        }
    }

    public void start() {
        // listen to all port
        for(RedirectTarget target : channelMap.values()) {
            ClientPortListener listener = new ClientPortListener(target.channel, target.host, target.port);
            listener.start();
            log.info("Channel {} started. host={} port={}", target.channel, target.host, target.port);
        }
    }

    public static void main(String argv[]) throws Exception {
        try {
            // read config file
            FileInputStream fis = new FileInputStream(CLIENT_PROP_FILE);
            Properties props = new Properties();
            props.load(fis);
            fis.close();

            TCPRedirect client = new TCPRedirect(props);
            client.start();
        } catch(Exception e) {
            log.error("Exception!!! Application terminated.", e);
        }
    }
}
