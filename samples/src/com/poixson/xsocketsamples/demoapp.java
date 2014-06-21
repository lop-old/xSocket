package com.poixson.xsocketsamples;

import com.poixson.xsocket.xSocket;


public class demoapp {

	private static xSocket socketServer = null;
	private static xSocket socketClient = null;


	public static void main(String[] args) {
		// parse args
		boolean loadServer = true;
		boolean loadClient = true;
		for(int index = 0; index < args.length; index++) {
			final String arg = args[index].toLowerCase();
			switch(arg) {
			case "--help":
				System.out.println();
				System.out.println("");
				System.out.println("");
				System.out.println("");
				System.out.println("");
				System.out.println("");
				System.out.println("");
				System.out.println();
				return;
			case "-m":
			case "--mode":
				if(index+1 >= args.length || args[index+1].startsWith("-")) {
					System.err.println("Missing mode argument");
					System.exit(1);
				}
				index++;
				final String str = args[index];
				switch(str) {
				case "s":
				case "server":
					loadServer = true;
					loadClient = false;
					break;
				case "c":
				case "client":
					loadClient = true;
					loadServer = false;
					break;
				case "b":
				case "both":
					loadServer = true;
					loadClient = true;
					break;
				case "n":
				case "none":
					loadServer = false;
					loadClient = false;
					break;
				default:
					System.err.println("Unknown mode argument: "+str);
					System.exit(1);
				}
				break;
			case "port":
				break;
			default:
				System.err.println("Unknown argument: "+arg);
				System.exit(1);
			}
		}
		// load server socket
		// load client socket
		// send test data
	}


}
