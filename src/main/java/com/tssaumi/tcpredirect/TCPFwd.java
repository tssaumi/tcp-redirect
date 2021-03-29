package com.tssaumi.tcpredirect;

import java.io.*;
import java.net.*;
import java.text.DecimalFormat;
import java.util.concurrent.*;

import com.tssaumi.tcpredirect.SocketKiller;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TCPFwd {

    private long uid = -1L; // unique ID
    private BytesQueue qTo = null;
    private BytesQueue qBack = null;
    
    private String clientAddress = null;
    private String serverAddress = null;
    
    private Connection client = null;
    private Connection server = null;
    
    private enum TYPE {
    	CLIENT("C"),
    	SERVER("S");
    	
    	private String display;
    	
    	private TYPE(String display) {
    		this.display = display;
    	}
    	
    	@Override
        public String toString() {
            return display;
        }
    	
    };

    private boolean closeTriggered = false;
    private synchronized void closeDetected() {
    	if(closeTriggered == false) {
	    	// log data before die
	    	StringBuilder sb = new StringBuilder("[#"+uid+"] Capture connection info:");
	    	DecimalFormat df = new DecimalFormat("#,##0");
	    	sb.append("\r\n=================================================================");
	    	sb.append("\r\n(Client) ").append(clientAddress).append(" >>><<< ").append(serverAddress).append(" (Server)");
	    	sb.append("\r\nClient >>> Server queue size: ").append(df.format(qTo.getBytes()));
	    	sb.append("\r\nClient <<< Server queue size: ").append(df.format(qBack.getBytes()));
	    	sb.append("\r\nClient READ>>>WRITE(bytes): ").append(df.format(client.getAccumInBytes())).append(" >>> ").append(df.format(server.getAccumOutBytes()));
	    	sb.append("\r\nServer READ>>>WRITE(bytes): ").append(df.format(server.getAccumInBytes())).append(" >>> ").append(df.format(client.getAccumOutBytes()));
	    	sb.append("\r\n=================================================================");
	    	log.info(sb.toString());
    	}
    	closeTriggered = true;
    	
        close();
    }

    /**
     * LinkedBlockingQueue which keep current bytes buffered.
     */
    class BytesQueue extends LinkedBlockingQueue<byte[]> {
        private long totalBytes = 0;
        
        public long getBytes() {
            return totalBytes;
        }
        
        private final Object SYN_OBJ = new Object();
        public void addBytes(long len) {
            synchronized(SYN_OBJ) {
                totalBytes += len;
            }
        }
        
        public void removeBytes(long len) {
            synchronized(SYN_OBJ) {
                totalBytes -= len;
            }
        }
        
        @Override
        public void put(byte[] e) throws InterruptedException {
            super.put(e);
            if(e instanceof byte[]) {
                byte[] bytes = (byte[])e;
                addBytes(bytes.length);
            }
        }
        
        @Override
        public byte[] take() throws InterruptedException {
        	byte[] e = super.take();
        	if(e != null) {
        		removeBytes(e.length);
        	}
        	return e;
        }
        
        @Override
        public byte[] poll() {
        	byte[] e = super.poll();
        	if(e != null) {
        		removeBytes(e.length);
        	}
        	return e;
        }
        
        @Override
        public byte[] poll(long timeout, TimeUnit unit) throws InterruptedException {
            byte[] e = super.poll(timeout, unit);
            if(e != null) {
            	removeBytes(e.length);
            }
            return e;
        }
    }
    
    public TCPFwd(long uid, Socket clientSocket, Socket serverSocket, boolean clientPaused, boolean serverPaused) {
        this.uid = uid;
        this.qTo = new BytesQueue();
        this.qBack = new BytesQueue();
        
        try {
            this.clientAddress = clientSocket.getRemoteSocketAddress().toString();
            this.serverAddress = serverSocket.getRemoteSocketAddress().toString();
            
            client = new Connection(uid, TYPE.CLIENT, clientAddress, clientSocket, qTo, qBack, clientPaused);
            server = new Connection(uid, TYPE.SERVER, serverAddress, serverSocket, qBack, qTo, serverPaused);
        } catch(Exception e) {
            log.error("Fail to start TCP Forwarding. client={} server={}", clientSocket, serverSocket, e);
            close();
        }
    }
    
    public void close() {
        closeClient();
        closeServer();
    }

    public void closeClient() {
        if(client != null)
            client.close();
    }
    
    public void closeServer() {
        if(server != null)
            server.close();
    }

    class Connection {
        private long uid = -1L;
        private TYPE type = null; // C=Client; S=Server
        
        private String host = null;
        
        private Socket socket = null;
        private InputStream is = null;
        private OutputStream os = null;
        
        private InThread tIn = null;
        private OutThread tOut = null;
        
        public Connection(long uid, TYPE type, String host, Socket socket, BytesQueue qIn, BytesQueue qOut, boolean paused) throws IOException {
            this.uid = uid;
            this.type = type;
            this.host = host;
            this.socket = socket;
            is = socket.getInputStream();
            os = socket.getOutputStream();
            
            tIn = new InThread(""+uid+"-"+type+"(R)", host, is, qIn, paused);
            tOut = new OutThread(""+uid+"-"+type+"(W)", host, os, qOut, paused);
            
            tIn.start();
            tOut.start();
        }
        
        public long getAccumOutBytes() {
        	return tOut.getAccumBytes();
        }
        
        public long getAccumInBytes() {
        	return tIn.getAccumBytes();
        }

        public void close() {
        	// 1) stop threads
            if(tIn != null) {
                tIn.stopNow();
            }
            if(tOut != null) {
                tOut.stopNow();
            }
            
            // 2) close socket
            SocketKiller.killSocket(socket, is, os);
            this.socket = null;
            this.is = null;
            this.os = null;
        }
    }
    
    /**
     * Read data from InputStream, and put to queue.
     */
    class InThread extends Thread {
        private volatile boolean running = false;

        private String host = null;
        
        private BytesQueue q = null;
        private InputStream is = null;
        
        private final long minRespTimeMs = 100L;   // min response time = 100 ms
        
        // statics
        @Getter private long accumBytes = 0;

        public InThread(String name, String host, InputStream is, BytesQueue q, boolean pause) {
            super(name);
            this.host = host;
            this.is = is;
            this.q = q;
        }
        
        public void run() {
            byte[] buffer = new byte[40960];   // 40K
            running = true;
            try {
                while(running) {
                    boolean sleepRequired = false;
                    
                    int len = is.read(buffer);
                    if(len < 0) {
                        // connection closed!
                        log.error("[#{}] READ {} bytes from {}! END OF STREAM detected!", uid, len, host);
                        running = false;
                    } else {
                        if(len > 0) {
                            byte[] dataTrunk = new byte[len];
                            System.arraycopy(buffer, 0, dataTrunk, 0, len);
                            q.put(dataTrunk);
                            accumBytes += len;
                        } else {
                            log.warn("Read zero byte from stream!");
                        }
                    }
                }
            } catch(Exception e) {
            	if(running) {
            		log.error("[#{}]Exception in READ! host={}", uid, host, e);
            	}
            } finally {
        		running = false;
        		try {
        			q.put(new byte[0]);
        		} catch(Exception ex) {
        			log.error("Fail to put dead signal in queue...", ex);
        		}
            }
            log.info("[#{}][THREAD END] Stop READ from: {}", uid, host);
            closeDetected();
        }
        
        public void stopNow() {
            running = false;
        }
    }
    
    /**
     * Poll data from queue and write data to OutputStream
     */
    class OutThread extends Thread {
        private volatile boolean running = false;
        private volatile boolean pause = false;
        private volatile boolean skipData = false;
        
        private String host = null;

        private BytesQueue q = null;
        private OutputStream os = null;
        
        private final int WRITE_BUF_SIZE = 40960;	// 40K
        private int curBufSize = 0;
        private byte[] writeBuffer = new byte[WRITE_BUF_SIZE];
        
        /**
         * Time of latest writing to OUT stream.<BR>
         * = System.nanoTime()
         */
        @Getter private long lastWriteTimeNs = 0;
        
        private static final long SLEEP_MS = 100L;   // min response time = 100 ms
        
        // statics
        @Getter private long accumBytes = 0;
        @Getter private long accumSkipBytes = 0;	// bytes that thrown away
        
        public OutThread(String name, String host, OutputStream os, BytesQueue q, boolean pause) {
            super(name);
            this.host = host;
            this.os = os;
            this.q = q;
            this.pause = pause;
        }
        
        public void run() {
            running = true;
            try {
                while(running) {
                    if(pause == false) {
                    	byte[] dataTrunk = null;
                    	if(curBufSize == 0) {
                    		dataTrunk = q.take();
                    	} else {
                    		dataTrunk = q.poll();
                    	}
                        if(dataTrunk != null) {
                            int dataSize = dataTrunk.length;
                            if(dataSize == 0) {
                            	// get dead signal!
                            	running = false;
                            	continue;
                            }
                            if(skipData == false) {
                            	if((WRITE_BUF_SIZE - curBufSize) > dataSize) {
                            		System.arraycopy(dataTrunk, 0, writeBuffer, curBufSize, dataSize);
                            		curBufSize += dataSize;
                            	} else {
                            		if(curBufSize > 0 ) {
	                            		lastWriteTimeNs = System.nanoTime();
	                            		os.write(writeBuffer, 0 , curBufSize);
	                                    accumBytes += curBufSize;
	                            		curBufSize = 0;
                            		}
                            		if(dataSize >= WRITE_BUF_SIZE) {
	                            		lastWriteTimeNs = System.nanoTime();
	                            		os.write(dataTrunk);
	                                    accumBytes += dataSize;
                            		} else {
                            			System.arraycopy(dataTrunk, 0, writeBuffer, curBufSize, dataSize);
                            			curBufSize += dataSize;
                            		}
                            	}
                            } else {
                                accumSkipBytes += dataSize;
                            }
                        } else {
                        	// nothing polled
                        	if(curBufSize > 0) {
                        		lastWriteTimeNs = System.nanoTime();
                        		os.write(writeBuffer, 0, curBufSize);
                        		accumBytes += curBufSize;
                        		curBufSize = 0;
                        	}
                        }
                    } else {
                    	// paused
                    	if(SLEEP_MS > 0) {
                    		sleep(SLEEP_MS);
                    	}
                    }
                }
            } catch(Exception e) {
            	if(running) {
            		log.error("[#{}]Exception in WRITE! host={}", uid, host, e);
            	}
            }
            log.info("[#{}][THREAD END] Stop WRITE to: {}", uid, host);
            closeDetected();
        }
        
        public void setPause(boolean pause) {
            this.pause = pause;
        }
        
        public void setSkipData(boolean skipData) {
            this.skipData = skipData;
        }
        
        public void stopNow() {
            running = false;
        }
    }
}