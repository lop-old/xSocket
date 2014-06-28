package com.poixson.xsocketsamples;

import java.io.PrintWriter;

import com.poixson.commonjava.Utils.utils;
import com.poixson.commonjava.Utils.utilsThread;
import com.poixson.xsocket.xSocket;
import com.poixson.xsocket.xSocket.xSocketWorker;
import com.poixson.xsocket.protocols.xSocketTCP;
import com.poixson.xsocket.protocols.xSocketUDP;


public class demoapp {

	private enum PROTOCOL {TCP, UDP, BOTH}

	private static xSocket socketServer = null;
	private static xSocket socketClient = null;
	private static xSocketWorker clientWorker = null;


	public static void main(final String[] args) {
		// parse args
		Boolean loadServer = null;
		Boolean loadClient = null;
		String  host    = null;
		Integer port    = null;
		Integer backlog = null;
		PROTOCOL protocol = PROTOCOL.BOTH;
		for(int index = 0; index < args.length; index++) {
			final String arg = args[index].toLowerCase();
			switch(arg) {

			case "--help":
		        // get jar self file name
		        final String jarSelfStr;
		        {
		            final String str = demoapp.class.getProtectionDomain().getCodeSource().getLocation().getFile();
		            final int pos = str.lastIndexOf('/');
		            if(pos == -1)
		                jarSelfStr = str;
		            else
		                jarSelfStr = str.substring(pos + 1);
		        }
				System.out.println();
				System.out.println("Usage:");
				System.out.println(" java -jar "+jarSelfStr+" [options]");
				System.out.println();
				System.out.println("Options:");
				System.out.println("  -r, --protocol <tcp/udp>  TCP/UDP protocol to use");
				System.out.println("  -t, --udp                 Use TCP protocol");
				System.out.println("  -u, --tcp                 Use UDP protocol");
				System.out.println();
				System.out.println("  -m, --mode <server/client>  Server and/or client mode sockets");
				System.out.println("  -s, --server                Server mode socket");
				System.out.println("  -c, --client                Client mode socket");
				System.out.println();
				System.out.println("  -h, --host <hostname/ip>  Host to listen on or connect to");
				System.out.println("  -p, --port <port>         Port to listen on or connect to");
				System.out.println();
				System.out.println("  -b, -backlog <queue>  Connection backlog for server sockets");
				System.out.println();
				System.out.println("  --help         Display this help and exit");
				System.out.println("  -v, --version  Display version information and exit");
				System.out.println();
				System.exit(0);

			case "-r":
			case "--protocol":
				if(index+1 >= args.length || args[index+1].startsWith("-")) {
					System.err.println("Missing protocol argument");
					System.exit(1);
				}
				index++;
				switch(args[index].toLowerCase()) {
				case "t":
				case "tcp":
					protocol = PROTOCOL.TCP;
					System.out.println("Using TCP protocol");
					break;
				case "u":
				case "udp":
					protocol = PROTOCOL.UDP;
					System.out.println("Using UDP protocol");
					break;
				case "b":
				case "both":
					protocol = PROTOCOL.BOTH;
					System.out.println("Using TCP and UDP protocols");
					break;
				default:
					System.err.println("Unknown protocol argument: "+args[index]);
					System.exit(1);
				}
				break;
			case "-t":
			case "--tcp":
				protocol = PROTOCOL.TCP;
				System.out.println("Using TCP protocol");
				break;
			case "-u":
			case "--udp":
				protocol = PROTOCOL.UDP;
				System.out.println("Using UDP protocol");
				break;

			case "-m":
			case "--mode":
				if(index+1 >= args.length || args[index+1].startsWith("-")) {
					System.err.println("Missing mode argument");
					System.exit(1);
				}
				index++;
				switch(args[index].toLowerCase()) {
				case "s":
				case "server":
					loadServer = Boolean.TRUE;
					loadClient = Boolean.FALSE;
					System.out.println("Using server socket only");
					break;
				case "c":
				case "client":
					loadClient = Boolean.TRUE;
					loadServer = Boolean.FALSE;
					System.out.println("Using client socket only");
					break;
				case "b":
				case "both":
					loadServer = Boolean.TRUE;
					loadClient = Boolean.TRUE;
					System.out.println("Using client and server sockets");
					break;
				default:
					System.err.println("Unknown mode argument: "+args[index]);
					System.exit(1);
				}
				break;
			case "-s":
			case "--server":
				loadServer = Boolean.TRUE;
				System.out.println("Using server socket");
				break;
			case "-c":
			case "--client":
				loadClient = Boolean.TRUE;
				System.out.println("Using client socket");
				break;

			case "-h":
			case "--host":
				if(index+1 >= args.length || args[index+1].startsWith("-")) {
					System.err.println("Missing host argument");
					System.exit(1);
				}
				index++;
				host = args[index];
				break;
			case "-p":
			case "--port":
				if(index+1 >= args.length || args[index+1].startsWith("-")) {
					System.err.println("Missing port argument");
					System.exit(1);
				}
				index++;
				try {
					port = new Integer(Integer.parseInt(args[index]));
				} catch (NumberFormatException ignore) {
					System.err.println("Invalid port argument, must be a number");
					System.exit(1);
				}
				break;
			case "-b":
			case "--backlog":
				if(index+1 >= args.length || args[index+1].startsWith("-")) {
					System.err.println("Missing backlog argument");
					System.exit(1);
				}
				index++;
				try {
					backlog = new Integer(Integer.parseInt(args[index]));
				} catch (NumberFormatException ignore) {
					System.err.println("Invalid backlog argument, must be a number");
					System.exit(1);
				}
				break;

			default:
				System.err.println("Unknown argument: "+arg);
				System.exit(1);
			}
		}

		// default values
		if(loadServer == null && loadClient == null) {
			loadServer = Boolean.TRUE;
			loadClient = Boolean.TRUE;
			System.out.println("Using both client and server sockets by default");
		} else {
			if(loadServer == null) loadServer = Boolean.FALSE;
			if(loadClient == null) loadClient = Boolean.FALSE;
		}
		if(host == null)    host = "127.0.0.1";
		if(port == null)    port = new Integer(80);
		if(backlog == null) backlog = new Integer(2);
		System.out.println();
		System.out.println("Starting socket tests..");

		// load server socket
		if(loadServer.booleanValue()) {
			if(PROTOCOL.TCP.equals(protocol) || PROTOCOL.BOTH.equals(protocol))
				socketServer = xSocketTCP.get();
			else if(PROTOCOL.UDP.equals(protocol) || PROTOCOL.BOTH.equals(protocol))
				socketServer = xSocketUDP.get();
			socketServer
				.setHost(host)
				.setPort(port.intValue())
				.setBacklog(backlog.intValue());
			socketServer.startServer();
		}

		// load client socket
		if(loadClient.booleanValue()) {
			if(PROTOCOL.TCP.equals(protocol) || PROTOCOL.BOTH.equals(protocol))
				socketClient = xSocketTCP.get();
			else if(PROTOCOL.UDP.equals(protocol) || PROTOCOL.BOTH.equals(protocol))
				socketClient = xSocketUDP.get();
			socketClient
				.setHost(host)
				.setPort(port.intValue());
			clientWorker = socketClient.connect();
		}

		// send test data
		System.out.println();
		// simple send()
		clientWorker.send("send() !!! test out to client !!!\n");
		utilsThread.Sleep(100);
		// output stream
		final PrintWriter writer = new PrintWriter(clientWorker.out(), false);
		writer.println("out()  !!! test out to client !!!");
		writer.flush();
		writer.close();

		// close sockets
		utilsThread.Sleep(100);
		System.out.println();
		System.out.print("Closing sockets.");
		utilsThread.Sleep(500);
		System.out.print(".");
		utilsThread.Sleep(500);
		System.out.print(".");
		utilsThread.Sleep(500);
		System.out.println();

		utils.safeClose(socketServer);
		utils.safeClose(socketClient);
		System.out.println();
	}


}
