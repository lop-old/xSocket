package com.poixson.xsocket;

import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.poixson.commonjava.EventListener.xHandler;
import com.poixson.commonjava.EventListener.xListener;
import com.poixson.commonjava.Utils.utils;
import com.poixson.commonjava.Utils.xCloseable;
import com.poixson.commonjava.xLogger.xLog;
import com.poixson.xsocket.xSocket.xSocketWorker.xSocketWorkerFactory;
import com.poixson.xsocket.protocols.xSocketUDP;


public abstract class xSocket implements Runnable, xCloseable {

	public static final String VERSION = "1.0.0";

	// constants
	public static final int MAX_PORT_NUMBER = 65534;
	public static final int DEFAULT_BACKLOG = 10;
	public static final int MAX_BACKLOG = 50;
	public static final int DEFAULT_TIMEOUT = 5000;

	public static final String UTF8 = "UTF-8";

	protected volatile SocketState state = null;
	public enum SocketState {
		CLOSED,
		RESOLVING,
		UNKNOWN_HOST,
		CONNECTING,
		LISTENING,
		CONNECTED
	}


	// worker and factory
	public interface xSocketWorker extends Runnable, xCloseable {
		public interface xSocketWorkerFactory {
			public xSocketWorker tcpWorker(final Socket socket);
			public xSocketWorker udpWorker(final DatagramPacket packet);
			public xSocketWorker udpWorker(final xSocketUDP socket);
		}
		public boolean send(final String data);
		public OutputStream out();
		public boolean flush();
		public SocketState getSocketState();
	}


	// worker factory
	private volatile xSocketWorkerFactory factory = null;
	private final Set<xSocketWorker> workers = new HashSet<xSocketWorker>();

	// event handler
	private final xHandler handlers = new xHandler();

	// socket listener thread
	private volatile Thread listenerThread = null;
	private final Object threadLock = new Object();

	// connection parameters
	private volatile String host = null;
	private volatile int port = 0;
	private volatile int backlog = 0;


	public xSocket() {
	}
	@Override
	public Object clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}


	/**
	 * Start listener thread (socket server)
	 */
	public void startServer() {
		// start listening thread
		if(this.listenerThread == null) {
			synchronized(this.threadLock) {
				if(this.listenerThread == null) {
					this.listenerThread = new Thread() {
						private volatile xSocket sock = null;
						public Thread init(final xSocket socket) {
							this.sock = socket;
							return this;
						}
						@Override
						public void run() {
							this.sock.run();
						}
					}.init(this);
				}
			}
			this.listenerThread.start();
			try {
				// wait for startup
				for(int i=0; i<1000; i++) {
					if(this.state != null) break;
					Thread.sleep(5);
				}
				if(SocketState.CLOSED.equals(this.state))       return;
				if(SocketState.UNKNOWN_HOST.equals(this.state)) return;
				// wait for listening or error
				for(int i=0; i<1000; i++) {
					if(SocketState.CLOSED.equals(this.state))    break;
					if(SocketState.LISTENING.equals(this.state)) break;
					Thread.sleep(5);
				}
			} catch (InterruptedException ignore) {
				utils.safeClose(this);
				setState(SocketState.CLOSED);
			}
		}
	}
	/**
	 * Socket listener loop
	 */
	@Override
	public abstract void run();


	public abstract xSocketWorker connect();


	@Override
	public void close() {
		if(this.workers.size() > 0) {
			final Iterator<xSocketWorker> it = this.workers.iterator();
			while(it.hasNext())
				utils.safeClose(it.next());
		}
	}
	@Override
	public abstract boolean isClosed();


	public abstract boolean send(final String remoteHost, final int remotePort, final String data);


	protected void setState(final SocketState state) {
		if(state == null) throw new NullPointerException();
		this.state = state;
//TODO: state changed event
	}
	public SocketState getSocketState() {
		return this.state;
	}


	// ------------------------------------------------------------------------------- //
	// connection parameters


	/**
	 * Remote host or local binding address
	 */
	public xSocket setHost(final String host) {
		this.host = (host == null || host.isEmpty()) ? null : host;
		return this;
	}
	/**
	 * Remote host or local binding address
	 */
	public String getHost() {
		return this.host;
	}
	/**
	 * Resolve the stored hostname
	 * @return InetAddress
	 */
	public InetAddress usingHost() throws UnknownHostException {
		final String hostStr = getHost();
		if(hostStr == null || hostStr.equals("*") || hostStr.equalsIgnoreCase("any"))
			return null;
		else if("127.0.0.1".equals(hostStr) || "localhost".equalsIgnoreCase(hostStr))
			return InetAddress.getLoopbackAddress();
		else {
			log().finest("resolving "+getHost());
			final InetAddress addr = InetAddress.getByName(hostStr);
			log().finest("resolved "+getHost()+" to "+(addr == null ? "?" : addr.toString()));
			return addr;
		}
	}


	/**
	 * Local or remote port
	 * @param port Port number to use with a socket
	 */
	public xSocket setPort(final int port) {
		this.port = port;
		return this;
	}
	/**
	 * Local or remote port
	 * @param port Port number to use with sockets
	 */
	public xSocket setPort(final Integer port) {
		this.port = (port == null) ? 0 : port.intValue();
		return this;
	}
	/**
	 * Local or remote port
	 * @return Port number being used for sockets
	 */
	public int getPort() {
		return this.port;
	}
	/**
	 * Validates the port parameter.
	 * @return Validated int port number.
	 */
	public int usingPort() {
		final int prt = getPort();
		if(prt == Integer.MIN_VALUE)
			throw new IllegalArgumentException("port not set");
		if(prt < 1)
			throw new IllegalArgumentException("port out of range ( "+
				Integer.toString(prt)+" < 1 )");
		if(prt > MAX_PORT_NUMBER)
			throw new IllegalArgumentException("port out of range ( "+
				Integer.toString(prt)+" > "+Integer.toString(MAX_PORT_NUMBER)+" )");
		return prt;
	}


	/**
	 * Connection backlog
	 * @param value Maximum number of connections to queue for acceptance
	 * @return
	 */
	public xSocket setBacklog(final int value) {
		this.backlog = value;
		return this;
	}
	/**
	 * Connection backlog
	 * @param value Maximum number of connections to queue for acceptance
	 * @return
	 */
	public xSocket setBacklog(final Integer value) {
		this.backlog = (value == null) ? 0 : value.intValue();
		return this;
	}
	/**
	 * Connection backlog
	 * @return Maximum number of connections to queue for acceptance
	 */
	public int getBacklog() {
		return this.backlog;
	}
	/**
	 * Validates the backlog parameter.
	 * @return Validated int number within usable range.
	 */
	public int usingBacklog() {
		final int back = getBacklog();
		if(back == 0)
			return DEFAULT_BACKLOG;
		if(back < 1)
			throw new IllegalArgumentException("backlog out of range ( "+
				Integer.toString(back)+" < 1 )");
		if(back > MAX_BACKLOG)
			throw new IllegalArgumentException("backlog out of range ( "+
				Integer.toString(back)+" > "+Integer.toString(MAX_BACKLOG)+" )");
		return back;
	}


	// ------------------------------------------------------------------------------- //
	// register / unregister


	// register event listener
	public void register(final xListener listener) {
		this.handlers.register(listener);
	}
	// unregister event listener
	public void unregister(final xListener listener) {
		this.handlers.unregister(listener);
	}


	// add worker
	public void register(final xSocketWorker worker) {
		synchronized(this.workers) {
			this.workers.add(worker);
		}
	}
	// remove worker
	public void unregister(final xSocketWorker worker) {
		synchronized(this.workers) {
			this.workers.remove(worker);
		}
	}


	// worker factory
	public xSocket setWorkerFactory(final xSocketWorkerFactory factory) {
		this.factory = factory;
		return this;
	}
	public xSocketWorkerFactory getWorkerFactory() {
		return this.factory;
	}


	// ------------------------------------------------------------------------------- //


	// logger
	private volatile xLog _log = null;
	private final Object logLock = new Object();
	public xLog log() {
		if(this._log == null) {
			synchronized(this.logLock) {
				if(this._log == null) {
					final String hst = getHost();
					this._log = xLog.getRoot("xSocket-"+(hst == null || hst.equals("*") ? "any" : hst)+":"+getPort());
				}
			}
		}
		return this._log;
	}
	public xLog log(final String name) {
		return log().get(name);
	}
	public void setLog(final xLog log) {
		synchronized(this.logLock) {
			this._log = log;
		}
	}


}
