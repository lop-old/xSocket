package com.poixson.xsocket.protocols;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import com.poixson.commonjava.Utils.utils;
import com.poixson.commonjava.Utils.utilsThread;
import com.poixson.xsocket.xSocket;
import com.poixson.xsocket.xSocket.xSocketWorker.xSocketWorkerFactory;


public class xSocketTCP extends xSocket {

	private volatile ServerSocket server = null;
	private final Object runLock = new Object();


	// constructor
	public static xSocketTCP get() {
		return new xSocketTCP();
	}
	public xSocketTCP() {
		setWorkerFactory(new tcpWorkerFactory());
	}


	/**
	 * TCP worker class (connection handler)
	 */
	public class tcpWorker extends Thread implements xSocketWorker {

		private final Socket socket;
		private final BufferedReader in;
		private final OutputStream out;


		public tcpWorker(final Socket socket) throws IOException {
			this.socket = socket;
			this.in = new BufferedReader(
				new InputStreamReader(
					this.socket.getInputStream()
				)
			);
			this.out = socket.getOutputStream();
			this.start();
		}
		@Override
		public void run() {
			try {
				// data receiver loop
				while(!this.socket.isClosed()) {
					String data;
					try {
						data = this.in.readLine();
					} catch (SocketException ignore) {
						break;
					} catch (IOException e) {
						e.printStackTrace();
						break;
					}
					if(data == null) break;
//TODO: data received event
					System.out.println("RX-DATA: "+data);
				}
			} finally {
				utils.safeClose(this.in);
				utils.safeClose(this.socket);
				log().info("TCP connection closed");
			}
		}


		@Override
		public void close() {
			utils.safeClose(this.socket);
		}
		@Override
		public boolean isClosed() {
			if(this.socket == null)
				return true;
			return this.socket.isClosed();
		}


		@Override
		public boolean send(final String data) {
			if(isClosed()) return false;
			if(utils.isEmpty(data)) throw new NullPointerException();
			try {
				this.out.write(data.getBytes());
				this.out.flush();
			} catch (IOException ignore) {
				return false;
			}
			return true;
		}
		@Override
		public OutputStream out() {
			if(isClosed()) return null;
			return this.out;
		}
		@Override
		public boolean flush() {
			if(isClosed()) return false;
			try {
				this.out.flush();
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
	 * TCP worker factory
	 */
	public class tcpWorkerFactory implements xSocketWorkerFactory {


		@Override
		public xSocketWorker tcpWorker(final Socket socket) {
			try {
				return new tcpWorker(socket);
			} catch (IOException ignore) {}
			return null;
		}
		@Override
		public xSocketWorker udpWorker(final DatagramPacket packet) {
			throw new IllegalAccessError();
		}
		@Override
		public xSocketWorker udpWorker(final xSocketUDP socket) {
			throw new IllegalAccessError();
		}


	}


	/**
	 * Start TCP socket server
	 */
	@SuppressWarnings("resource")
	@Override
	public void run() {
		if(this.server != null) throw new RuntimeException("TCP socket server already running");
		try {
		synchronized(this.runLock) {
			if(this.server != null) throw new RuntimeException("TCP socket server already running");
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
			final int back = usingBacklog();
			log().fine("xSocket TCP server listening on "+
					(addr == null ? "*" : addr.toString())+":"+Integer.toString(prt));
			try {
				this.server = new ServerSocket(prt, back, addr);
			} catch (IOException e) {
				log().trace(e);
				return;
			}
			setState(SocketState.LISTENING);
			// connection listener loop
			while(!isClosed()) {
				// wait for a connection
				final Socket socket;
				try {
					socket = this.server.accept();
				} catch (SocketException ignore) {
					break;
				} catch (IOException e) {
					log().trace(e);
					break;
				}
				if(isClosed()) break;
				log().stats("Accepted connection from "+socket.getRemoteSocketAddress().toString().substring(1)+":"+Integer.toString(prt));
				// pass to worker
				final xSocketWorkerFactory factory = getWorkerFactory();
				if(factory == null) throw new RuntimeException("worker factory not set");
				final xSocketWorker worker = factory.tcpWorker(socket);
				register(worker);
			}
		}
		} catch (Exception e) {
			log().trace(e);
		} finally {
			utils.safeClose(this);
			setState(SocketState.CLOSED);
			log().info("Closed TCP socket server");
		}
	}


	/**
	 * Start TCP socket client
	 */
	@SuppressWarnings("resource")
	@Override
	public xSocketWorker connect() {
		Socket socket = null;
		xSocketWorker worker = null;
		try {
			final xSocketWorkerFactory factory = getWorkerFactory();
			if(factory == null) throw new RuntimeException("worker factory not set");
			final String hostStr = getHost();
			if(utils.isEmpty(hostStr) || "*".equals(hostStr) || "any".equalsIgnoreCase(hostStr)) {
				log().severe("remote host not set");
				return null;
			}
			// resolve host
			setState(SocketState.RESOLVING);
			final InetAddress addr;
//TODO: event RESOLVING
			try {
				addr = usingHost();
			} catch (UnknownHostException ignore) {
//TODO: event UNKNOWN_HOST
				log().warning("Unknown host: "+hostStr);
				return null;
			}

			// start connecting
			final int prt = usingPort();
			log().fine("xSocket TCP client connecting to "+addr.toString()+":"+Integer.toString(prt));
			setState(SocketState.CONNECTING);
			try {
				socket = new Socket(addr, prt);
			} catch (SocketException e) {
				log().severe(e.getMessage());
				return null;
			} catch (IOException e) {
				log().trace(e);
				return null;
			}
			if(!socket.isConnected()) {
				log().warning("Failed, not connected..");
				return null;
			}
			// pass to worker
			worker = factory.tcpWorker(socket);
			log().info("Connected to "+addr.toString()+":"+Integer.toString(prt));
		} catch (Exception e) {
			log().trace(e);
			setState(SocketState.CLOSED);
			return null;
		} finally {
			if(worker == null) {
				utils.safeClose(socket);
				setState(SocketState.CLOSED);
				return null;
			}
		}
		return worker;
	}


	@Override
	public boolean send(final String remoteHost, final int remotePort, final String data) {
		throw new IllegalAccessError();
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
