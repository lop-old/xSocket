package com.poixson.xsocket.protocols;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import com.poixson.commonjava.Utils.utils;
import com.poixson.commonjava.Utils.utilsThread;
import com.poixson.xsocket.xSocket;
import com.poixson.xsocket.xSocket.xSocketWorker.xSocketWorkerFactory;


public class xSocketUDP extends xSocket {

	private volatile DatagramSocket server = null;
	private final Object runLock = new Object();


	// constructor
	public static xSocketUDP get() {
		return new xSocketUDP();
	}
	public xSocketUDP() {
		setWorkerFactory(new udpWorkerFactory());
	}


	/**
	 * UDP worker class (connection handler)
	 */
	protected class udpWorker implements xSocketWorker {

		private final DatagramPacket packet;
		private final Thread thread;
		private final xSocketUDP socket;


		// udp server
		public udpWorker(final DatagramPacket packet) {
			this.packet = packet;
			this.thread = new Thread() {
				private volatile udpWorker worker = null;
				public Thread init(final udpWorker workr) {
					this.worker = workr;
					return this;
				}
				@Override
				public void run() {
					this.worker.run();
				}
			}.init(this);
			this.thread.start();
			this.socket = null;
		}
		// udp client
		public udpWorker(final xSocketUDP socket) {
			this.packet = null;
			this.thread = null;
			this.socket = socket;
		}
		@Override
		public void run() {
			final String data = new String(this.packet.getData());
//TODO: data received event
			System.out.println("RX-DATA: "+data);
		}


		@Override
		public void close() {
		}
		@Override
		public boolean isClosed() {
			return true;
		}


		@Override
		public boolean send(final String data) {
			try {
				final OutputStream out = out();
				out.write(data.getBytes());
				out.close();
			} catch (IOException ignore) {
				return false;
			}
			return true;
		}
		@Override
		public OutputStream out() {
			return new OutputStream() {
				private final StringBuilder str = new StringBuilder();
				private volatile xSocket sock = null;
				public OutputStream init(final xSocket sck) {
					this.sock = sck;
					return this;
				}
				@Override
				public void write(int b) throws IOException {
					this.str.append( (char) b );
				}
				@Override
				public void flush() throws IOException {
					if(this.str.length() == 0) return;
					super.flush();
					final String data = this.str.toString();
					this.str.setLength(0);
					this.sock.send(this.sock.getHost(), this.sock.getPort(), data);
				}
				@Override
				public void close() throws IOException {
					flush();
					super.close();
				}
			}.init(this.socket);
		}
		@Override
		public boolean flush() {
			try {
				out().flush();
			} catch (Exception ignore) {
				return false;
			}
			return true;
		}


		@Override
		public SocketState getSocketState() {
//TODO:
			return null;
		}


	}
	/**
	 * UDP worker factory
	 */
	public class udpWorkerFactory implements xSocketWorkerFactory {


		@Override
		public xSocketWorker tcpWorker(final Socket socket) {
			throw new IllegalAccessError();
		}
		@Override
		public xSocketWorker udpWorker(final DatagramPacket packet) {
			return new udpWorker(packet);
		}
		@Override
		public xSocketWorker udpWorker(final xSocketUDP socket) {
			return new udpWorker(socket);
		}


	}


	/**
	 * Start UDP socket server
	 */
	@Override
	public void run() {
		if(this.server != null) throw new RuntimeException("UDP socket server already running");
		try {
		synchronized(this.runLock) {
			if(this.server != null) throw new RuntimeException("UDP socket server already running");
			// resolve host
			setState(SocketState.RESOLVING);
			final InetAddress addr;
//TODO: event RESOLVING
			try {
				addr = usingHost();
			} catch (UnknownHostException ignore) {
//TODO: event UNKNOWN_HOST
				setState(SocketState.UNKNOWN_HOST);
				log().warning("Unknown host: "+getHost());
				return;
			}
			// start listening
			final int prt = usingPort();
			log().fine("xSocket UDP server listening on "+
					(addr == null ? "*" : addr.toString())+":"+Integer.toString(prt));
			try {
				this.server = new DatagramSocket(prt, addr);
			} catch (IOException e) {
				log().trace(e);
				return;
			}
			setState(SocketState.LISTENING);
			// connection listener loop
			while(!isClosed()) {
				// wait for a connection
				final byte[] buf = new byte[1024];
				final DatagramPacket packet = new DatagramPacket(buf, buf.length);
				try {
					this.server.receive(packet);
				} catch (SocketException ignore) {
					break;
				} catch (IOException e) {
					log().trace(e);
					break;
				}
				if(isClosed()) break;
				log().stats("Accepted connection from "+packet.getAddress().toString().substring(1)+":"+Integer.toString(prt));
				// pass to worker
				final xSocketWorkerFactory factory = getWorkerFactory();
				if(factory == null) throw new RuntimeException("worker factory not set");
				@SuppressWarnings("resource")
				final xSocketWorker worker = factory.udpWorker(packet);
				register(worker);
			}
		}
		} catch (Exception e) {
			log().trace(e);
		} finally {
			utils.safeClose(this);
			setState(SocketState.CLOSED);
			log().info("Closed UDP socket server");
		}
	}


	@Override
	public xSocketWorker connect() {
		xSocketWorkerFactory factory = getWorkerFactory();
		if(factory == null) throw new RuntimeException("worker factory not set");
		return factory.udpWorker(this);
	}


	@SuppressWarnings("resource")
	@Override
	public boolean send(final String remoteHost, final int remotePort, final String data) {
		if(utils.isEmpty(data)) throw new NullPointerException("data is required");
		if(utils.isEmpty(remoteHost) || "*".equals(remoteHost) || "any".equalsIgnoreCase(remoteHost)) {
			log().severe("remote host not set");
			return false;
		}
		// resolve host
		final InetAddress addr;
		try {
			if("127.0.0.1".equals(remoteHost) || "localhost".equalsIgnoreCase(remoteHost))
				addr = InetAddress.getLoopbackAddress();
			else {
				log().finest("resolving "+remoteHost);
				addr = InetAddress.getByName(remoteHost);
				log().finest("resolved "+remoteHost+" to "+(addr == null ? "?" : addr.toString()));
			}
		} catch (UnknownHostException ignore) {
//TODO: event UNKNOWN_HOST
//			setState(SocketState.UNKNOWN_HOST);
			log().warning("Unknown host: "+remoteHost);
			return false;
		}
		DatagramSocket client = null;
		try {
			client = new DatagramSocket();
			final byte[] bytes = data.getBytes();
			final DatagramPacket packet = new DatagramPacket(bytes, bytes.length, addr, usingPort());
			client.send(packet);
			return true;
		} catch (SocketException e) {
			log().trace(e);
			return false;
		} catch (IOException e) {
			log().trace(e);
			return false;
		} finally {
			utils.safeClose(client);
		}
	}


	@Override
	public void close() {
		super.close();
		if(this.server == null) return;
		utils.safeClose(this.server);
		utilsThread.Sleep(5);
	}
	@Override
	public boolean isClosed() {
		if(this.server == null)
			return true;
		if(this.server.isClosed())
			return true;
		if(!this.server.isBound()) {
			utils.safeClose(this.server);
			return true;
		}
		return false;
	}


}
