package com.tssaumi.tcpredirect;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static com.tssaumi.tcpredirect.Constants.SERVER_PROP_FILE;

/**
 * TCPRedirectServer:<BR>
 * TCP port forwarder which support multiple destination port forwarding<BR>
 * on <B>single server port</B>.<BR>
 * Note: Required redirect client (i.e. TCPRedirect) running in client side.<BR>
 * <BR>
 * <U><B>Example:</B></U><BR>
 * <BR>
 * <U>Situation:</U><BR>
 * Network A and Network B are two network. Machine in one network cannot directly access machine in another network.<BR>
 * <BR>
 * <U>Goal:</U><BR>
 * Some Firewall rules are changed to allow <B>Machine A1</B> in Network A to access <B>port 8080</B> of <B>Machine B1</B> in Network B.<BR>
 * Setup a redirect server to allow:<BR>
 * <UL>
 * <LI>Machine A2, A3 in Network A to access port 80 of Machine B4 in Network B.</LI>
 * <LI>Machine A4 in Network A to access port 25 of Machine B5 in Network B.</LI>
 * </UL>
 * 
 * Setup:<BR>
 * <OL>
 * <LI>Run redirect server in Machine B1 in Network B which listen to port 8080 and have config to enable following channel:<BR>
 *     Channel "A": Connect to Port 80 of Machine B4<BR>
 *     Channel "B": Connect to Port 25 of Machine B5</LI>
 * <LI>Run single redirect client (listen two ports) or two separated redirect client(each listen to a single port) in Machine A1 in Network A with following config:<BR>
 *     Connect to Redirect Server at Port 8080 of Machine B1 in Network B.<BR>
 *     1) Listen to (for example) local port 8080 for Channel "A"<BR>
 *     2) Listen to (for example) local port 8025 for Channel "B"</LI>
 * <LI>
 * <LI>Application on Machine A2, A3 connection to local port 8080</LI>
 * <LI>Application on Machine A4 connection to local port 8025</LI>
 * </OL>
 */

@Slf4j
public class TCPRedirectServer {
	
	@Setter @Getter private int localPort;
	@Setter @Getter private String bindAddr;

	protected Properties props = null;
	
	protected static final Map<String, RedirectTarget> channelMap = new ConcurrentHashMap<>();
	
	private static final int MAX_HAND_SHAKE_IDLE_MS = 5000;

	public static RedirectTarget getTarget(String channelId) {
		return channelMap.get(channelId);
	}

	protected boolean getBoolProp(Properties props, String name, boolean defVal) throws Exception {
		String str = props.getProperty(name);
		if(str == null) {
			return defVal;
		}
		if(str.equalsIgnoreCase("Y")
				|| str.equalsIgnoreCase("YES")
				|| str.equalsIgnoreCase("T")
				|| str.equalsIgnoreCase("TRUE")
				|| str.equalsIgnoreCase("1")) {
			return true;
		} else if(str.equalsIgnoreCase("N")
				|| str.equalsIgnoreCase("NO")
				|| str.equalsIgnoreCase("F")
				|| str.equalsIgnoreCase("FALSE")
				|| str.equalsIgnoreCase("0")) {
			return false;
		}
		return defVal;
	}

	protected long getLongProp(Properties props, String name, long defVal) throws Exception {
		String str = props.getProperty(name);
		if(str == null) {
			return defVal;
		}
		return Long.parseLong(str);
	}

	protected long getLongProp(Properties props, String name) throws Exception {
		String str = props.getProperty(name);
		return Long.parseLong(str);
	}
	
	protected int getIntProp(Properties props, String name, int defVal) throws Exception {
		String str = props.getProperty(name);
		if(str == null) {
			return defVal;
		}
		return Integer.parseInt(str);
	}

	protected int getIntProp(Properties props, String name) throws Exception {
		String str = props.getProperty(name);
		return Integer.parseInt(str);
	}
	
	protected void loadProperties() throws Exception {
		this.localPort = getIntProp(props, "local.port");
		this.bindAddr = props.getProperty("bind.addr");
		if(bindAddr != null) {
			bindAddr = bindAddr.trim();
		}

		int numTarget = getIntProp(props, "number.of.target", 0);
		if(numTarget <= 0) {
			log.warn("Properties \"number.of.target\" is invalid, still try read all target from property file. value={}", numTarget);
		}

		Set<String> names = props.stringPropertyNames();
		for(String name : names) {
			if(name.matches("target\\.[0-9]+")) {
				String prop = props.getProperty(name);
				if(prop == null || prop.isEmpty()) {
					throw new Exception("Invalid target! "+name+"="+prop);
				}

				try {
					StringTokenizer st = new StringTokenizer(prop, ",");
					String channel = st.nextToken().trim().toUpperCase();
					String host = st.nextToken().trim();
					int port = Integer.parseInt(st.nextToken());

					if(channel.isEmpty()) {
						throw new RedirectException("Empty channel ID in target! property: "+name);
					}
					if(host.isEmpty()) {
						throw new RedirectException("Empty host in target! property: "+name);
					}
					if(port <= 0) {
						throw new RedirectException("Invalid port! property: "+name);
					}

					if(channelMap.get(channel) != null) {
						throw new RedirectException("Channel ID ["+channel+"] is duplicated! property: "+name);
					}

					RedirectTarget target = new RedirectTarget();
					target.channel = channel;
					target.host = host;
					target.port = port;
					channelMap.put(channel, target);
					log.info("Added new target: {}", target);
				} catch(RedirectException e) {
					throw e;
				} catch(Exception e) {
					throw new Exception("Invalid target! "+name+"="+prop, e);
				}
			}
		}
	}

	public TCPRedirectServer(Properties props) throws Exception {
		this.props = props;
		loadProperties();
	}

	public void start() {
		PortListener pl = new PortListener(bindAddr, localPort);
		pl.start();
	}
	
	public static void main(String argv[]) throws Exception {
		try {
			// read config file
			FileInputStream fis = new FileInputStream(SERVER_PROP_FILE);
			Properties props = new Properties();
			props.load(fis);
			fis.close();
			
			TCPRedirectServer server = new TCPRedirectServer(props);
			server.start();
		} catch(Exception e) {
			log.error("Exception!!! Application terminated.", e);
		}
	}

}
