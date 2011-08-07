package net.gnehzr.tnoodle.server;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.concurrent.Executors;

import net.gnehzr.tnoodle.utils.Utils;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.sun.net.httpserver.HttpServer;

public class TNoodleServer {
	public static String NAME, VERSION;
	static {
		Package p = TNoodleServer.class.getPackage();

		NAME = p.getImplementationTitle();
		if(NAME == null) {
			NAME = TNoodleServer.class.getName();
		}
		VERSION = p.getImplementationVersion();
		if(VERSION == null) {
			VERSION = "devel";
		}
	}
	
	// TODO - check that the directories in www don't conflict with
	// the other root "directories" we're defining here
	//private static HashMap<String, HttpHandler> pathHandlers = new HashMap<String, HttpHandler>();
	
	//TODO - it would be nice to kill threads when the tcp connection is killed, not sure if this is possible, though
	public TNoodleServer(int port, File scrambleFolder, boolean browse) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

		server.createContext("/", new PluginDelegator());

		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		
		String addr = InetAddress.getLocalHost().getHostAddress() + ":" + port;
		System.out.println(NAME + "-" + VERSION + " started on " + addr);
		String url = "http://" + addr;
		//TODO - maybe it would make sense to open this url asap, that
		//       way the user's browser starts parsing tnt even as scramble
		//       plugins are being loaded
		if(browse) {
			if(Desktop.isDesktopSupported()) {
				Desktop d = Desktop.getDesktop();
				if(d.isSupported(Desktop.Action.BROWSE)) {
					try {
						URI uri = new URI(url);
						System.out.println("Opening " + uri + " in browser. Pass -n to disable this!");
						d.browse(uri);
						return;
					} catch(URISyntaxException e) {
						e.printStackTrace();
					}
				}
			}
			System.out.println("Sorry, it appears the Desktop api is not supported on your platform");
		}
		
		System.out.println("Visit " + url + " for a readme and demo.");
	}

	public static void main(String[] args) throws IOException {
		Launcher.wrapMain(args);

		OptionParser parser = new OptionParser();
		// TODO - optional url prefix?
		OptionSpec<Integer> portOpt = parser.
			acceptsAll(Arrays.asList("p", "port"), "The port to run the http server on").
				withOptionalArg().
					ofType(Integer.class).
					defaultsTo(8080);
		OptionSpec<File> scrambleFolderOpt = parser.
			accepts("scramblers", "The directory of the scramble plugins").
				withOptionalArg().
					ofType(File.class).
					defaultsTo(new File(Utils.getProgramDirectory(), "scramblers"));
		OptionSpec<?> noBrowserOpt = parser.acceptsAll(Arrays.asList("n", "nobrowser"), "Don't open the browser when starting the server");
		OptionSpec<?> noUpgradeOpt = parser.acceptsAll(Arrays.asList("u", "noupgrade"), "If an instance of " + NAME + " is running on the desired port, do not attempt to kill it and start up");
		OptionSpec<?> help = parser.acceptsAll(Arrays.asList("h", "help", "?"), "Show this help");
		try {
			OptionSet options = parser.parse(args);
			if(!options.has(help)) {
				/*if(options.has(cgiOpt)) {
					CgiHttpExchange cgiExchange = new CgiHttpExchange();
					HttpHandler handler = null;
					String path = cgiExchange.getRequestURI().getPath();
					for(int i = path.length(); i > 0; i--) {
						handler = pathHandlers.get(path.substring(0, i));
						if(handler != null) {
							break;
						}
					}
					if(handler == null) {
						System.out.print("Content-type: text/plain\n\n");
						System.out.println("No handler found for: " + cgiExchange.getRequestURI().getPath());
						return;
					}
					handler.handle(cgiExchange);
					return;
				}*/
				int port = options.valueOf(portOpt);
				File scrambleFolder = options.valueOf(scrambleFolderOpt);
				boolean openBrowser = !options.has(noBrowserOpt);
				try {
					new TNoodleServer(port, scrambleFolder, openBrowser);
				} catch(BindException e) {
					// If this port is in use, we assume it's an instance of
					// TNoodleServer, and ask it to commit honorable suicide.
					// After that, we can start up. If it was a TNoodleServer,
					// it hopefully will have freed up the port we want.
					URL url = new URL("http://localhost:" + port + "/kill/now");
					System.out.println("Detected server running on port " + port + ", maybe it's an old " + NAME + "?");
					if(options.has(noUpgradeOpt)) {
						System.out.println("noupgrade option set. You'll have to free up port " + port + " manually, or clear this option.");
						return;
					}
					System.out.println("Sending request to " + url + " to hopefully kill it.");
					URLConnection conn = url.openConnection();
					InputStream in = conn.getInputStream();
					in.close();
					// If we've gotten here, then the previous server may be dead,
					// lets try to start up.
					System.out.println("Hopefully the old server is now dead, trying to start up.");
					final int MAX_TRIES = 10;
					for(int i = 1; i <= MAX_TRIES; i++) {
						try {
							Thread.sleep(1000);
							System.out.println("Attempt " + i + "/" + MAX_TRIES + " to start up");
							new TNoodleServer(port, scrambleFolder, openBrowser);
							break;
						} catch(Exception ee) {
							ee.printStackTrace();
						}
					}
				}
				return;
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		parser.printHelpOn(System.out);
		System.exit(1); // non zero exit status
	}
}