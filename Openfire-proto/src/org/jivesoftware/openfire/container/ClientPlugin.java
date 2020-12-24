package org.jivesoftware.openfire.container;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.jasper.servlet.JasperInitializer;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.annotations.ServletContainerInitializersStarter;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.plus.webapp.PlusConfiguration;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.FragmentConfiguration;
import org.eclipse.jetty.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.webapp.MetaInfConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.jivesoftware.openfire.JMXManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientPlugin implements Plugin {
	
	private static final Logger Log = LoggerFactory.getLogger(ClientPlugin.class);
	
	private int clientPort = 80;
	private Server clientServer;
	private File pluginDir;
	private ContextHandlerCollection contexts;
	
	public ClientPlugin() {}

	protected void startup() {

        deleteLegacyWebInfLibFolder();

        // the number of threads allocated to each connector/port
        int serverThreads = 10;

        final QueuedThreadPool tp = new QueuedThreadPool();
        tp.setName("Jetty-QTP-Client");

        clientServer = new Server(tp);

        if (JMXManager.isEnabled()) {
            JMXManager jmx = JMXManager.getInstance();
            clientServer.addBean(jmx.getContainer());
        }

        // Create connector for http traffic if it's enabled.
        if (clientPort > 0) {
            final HttpConfiguration httpConfig = new HttpConfiguration();

            // Do not send Jetty info in HTTP headers
            httpConfig.setSendServerVersion( false );

            final ServerConnector httpConnector = new ServerConnector(clientServer, null, null, null, -1, serverThreads, new HttpConnectionFactory(httpConfig));

            // Listen on a specific network interface if it has been set.
            String bindInterface = getBindInterface();
            httpConnector.setHost(bindInterface);
            httpConnector.setPort(clientPort);
            clientServer.addConnector(httpConnector);
        }

        // Create a connector for https traffic if it's enabled.
//        try {
//            IdentityStore identityStore = null;
//            if (XMPPServer.getInstance().getCertificateStoreManager() == null){
//                Log.warn( "Admin console: CertificateStoreManager has not been initialized yet. HTTPS will be unavailable." );
//            } else {
//                identityStore = XMPPServer.getInstance().getCertificateStoreManager().getIdentityStore( ConnectionType.WEBADMIN );
//            }
//            if (identityStore != null && adminSecurePort > 0 )
//            {
//                if ( identityStore.getAllCertificates().isEmpty() )
//                {
//                    Log.warn( "Admin console: Identity store does not have any certificates. HTTPS will be unavailable." );
//                }
//                else
//                {
//                    if ( !identityStore.containsDomainCertificate() )
//                    {
//                        Log.warn( "Admin console: Using certificates but they are not valid for the hosted domain" );
//                    }
//
//                    final ConnectionManagerImpl connectionManager = ( (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager() );
//                    final ConnectionConfiguration configuration = connectionManager.getListener( ConnectionType.WEBADMIN, true ).generateConnectionConfiguration();
//                    final SslContextFactory sslContextFactory = new EncryptionArtifactFactory( configuration ).getSslContextFactory();
//
//                    final HttpConfiguration httpsConfig = new HttpConfiguration();
//                    httpsConfig.setSendServerVersion( false );
//                    httpsConfig.setSecureScheme( "https" );
//                    httpsConfig.setSecurePort( adminSecurePort );
//                    httpsConfig.addCustomizer( new SecureRequestCustomizer() );
//
//                    final HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory( httpsConfig );
//                    final SslConnectionFactory sslConnectionFactory = new SslConnectionFactory( sslContextFactory, org.eclipse.jetty.http.HttpVersion.HTTP_1_1.toString() );
//
//                    final ServerConnector httpsConnector = new ServerConnector( clientServer, null, null, null, -1, serverThreads, sslConnectionFactory, httpConnectionFactory );
//                    final String bindInterface = getBindInterface();
//                    httpsConnector.setHost(bindInterface);
//                    httpsConnector.setPort(adminSecurePort);
//                    clientServer.addConnector(httpsConnector);
//
//                    sslEnabled = true;
//                }
//            }
//        }
//        catch ( Exception e )
//        {
//            Log.error( "An exception occurred while trying to make available the admin console via HTTPS.", e );
//        }

        // Make sure that at least one connector was registered.
        if (clientServer.getConnectors() == null || clientServer.getConnectors().length == 0) {
            clientServer = null;
            // Log warning.
            log(LocaleUtils.getLocalizedString("admin.console.warning"));
            return;
        }

        createWebAppContext();

        HandlerCollection collection = new HandlerCollection();
        clientServer.setHandler(collection);
        collection.setHandlers(new Handler[] { contexts, new DefaultHandler() });

        try {
            clientServer.start();

            // Log the ports that the admin server is listening on.
            logClientPorts();
        }
        catch (Exception e) {
            Log.error("Could not start client server", e);
        }
    }
	
	private void deleteLegacyWebInfLibFolder() {
        /*
        See https://igniterealtime.atlassian.net/projects/OF/issues/OF-1647 - with the migration from Ant to Maven, Openfire
        needs less JAR files scattered around the file system. When upgrading from before 4.3.0, the old file are not
        removed by the installer, so this method attempts to remove them.
         */
        final Path libFolder = Paths.get(pluginDir.getAbsoluteFile().toString(), "webapp", "WEB-INF", "lib");
        if (!Files.exists(libFolder) || !Files.isDirectory(libFolder)) {
            // Nothing to do
            return;
        }

        final int maxAttempts = 10;
        int currentAttempt = 1;
        do {
            int backupSuffix = 1;
            String backupFileName;
            do {
                backupFileName = "lib.backup-" + backupSuffix;
                backupSuffix++;
            } while (Files.exists(libFolder.resolveSibling(backupFileName)));

            Log.warn("Renaming legacy admin WEB-INF/lib folder to {}. Attempt #{} {}", backupFileName, currentAttempt, libFolder);

            currentAttempt++;
            try {
                Files.move(libFolder, libFolder.resolveSibling(backupFileName));
            } catch (final IOException e) {
               Log.warn("Exception attempting to delete folder, will retry shortly", e);
            }
            if(Files.exists(libFolder)) {
                try {
                    Thread.sleep(1000);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.warn("Interrupted whilst sleeping - aborting attempt to rename lib folder", e);
                }
            }
        } while (Files.exists(libFolder) && currentAttempt <= maxAttempts && !Thread.currentThread().isInterrupted());

        if (!Files.exists(libFolder)) {
            // We succeeded, so continue
            return;
        }

        // The old lib folder still exists, will have to be deleted manully
        final String message = "The folder " + libFolder + " must be manually renamed or deleted before Openfire can start. Shutting down.";
        // Log this everywhere so it's impossible (?) to miss
        Log.debug(message);
        Log.info(message);
        Log.warn(message);
        Log.error(message);
        System.out.println(message);
        XMPPServer.getInstance().stop();
        throw new IllegalStateException(message);
    }
	
	protected void shutdown() {
        try {
            if (clientServer != null && clientServer.isRunning()) {
                clientServer.stop();
            }
        }
        catch (Exception e) {
            Log.error("Error stopping admin console server", e);
        }

        if (contexts != null ) {
            try {
                contexts.stop();
                contexts.destroy();
            } catch ( Exception e ) {
                Log.error("Error stopping admin console server", e);
            }
        }
        clientServer = null;
        contexts = null;
    }
	
	public String getBindInterface() {
        String adminInterfaceName = JiveGlobals.getXMLProperty("adminConsole.interface");
        String globalInterfaceName = JiveGlobals.getXMLProperty("network.interface");
        String bindInterface = null;
        if (adminInterfaceName != null && adminInterfaceName.trim().length() > 0) {
            bindInterface = adminInterfaceName;
        }
        else if (globalInterfaceName != null && globalInterfaceName.trim().length() > 0) {
            bindInterface = globalInterfaceName;
         }
        return bindInterface;
    }
	
	public ContextHandlerCollection getContexts() {
        return contexts;
    }

    /**
     * Restart the admin console (and it's HTTP server) without restarting the plugin.
     */
    public void restart() {
        try {
            shutdown();
            startup();
        }
        catch (Exception e) {
            Log.error("An exception occurred while restarting the admin console:", e);
        }
    }

    private void createWebAppContext() {

        contexts = new ContextHandlerCollection();

        WebAppContext context;
        // Add web-app. Check to see if we're in development mode. If so, we don't
        // add the normal web-app location, but the web-app in the project directory.
        boolean developmentMode = Boolean.getBoolean("developmentMode");
        if( developmentMode )
        {
            System.out.println(LocaleUtils.getLocalizedString("admin.console.devmode"));

            context = new WebAppContext(contexts, pluginDir.getParentFile().getParentFile().getParentFile().getParent() +
                    File.separator + "src" + File.separator + "plugins" + File.separator + "admin", "/");
        }
        else {
            context = new WebAppContext(contexts, pluginDir.getAbsoluteFile() + File.separator + "webapp",
                    "/");
        }

        // Ensure the JSP engine is initialized correctly (in order to be able to cope with Tomcat/Jasper precompiled JSPs).
        final List<ContainerInitializer> initializers = new ArrayList<>();
        initializers.add(new ContainerInitializer(new JasperInitializer(), null));
        context.setAttribute("org.eclipse.jetty.containerInitializers", initializers);
        context.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());
        context.setConfigurations(new Configuration[]{
            new AnnotationConfiguration(),
            new WebInfConfiguration(),
            new WebXmlConfiguration(),
            new MetaInfConfiguration(),
            new FragmentConfiguration(),
            new EnvConfiguration(),
            new PlusConfiguration(),
            new JettyWebXmlConfiguration()
        });
        final URL classes = getClass().getProtectionDomain().getCodeSource().getLocation();
        context.getMetaData().setWebInfClassesDirs(Collections.singletonList(Resource.newResource(classes)));

        // The index.html includes a redirect to the index.jsp and doesn't bypass
        // the context security when in development mode
        context.setWelcomeFiles(new String[]{"index.html"});
        
        // Make sure the context initialization is done when in development mode
        if( developmentMode )
        {
            context.addBean( new ServletContainerInitializersStarter( context ), true );
        }
    }

    private void log(String string) {
       Log.info(string);
       System.out.println(string);
    }
	
	@Override
	public void initializePlugin(PluginManager manager, File pluginDirectory) {
		this.pluginDir = pluginDirectory;

        startup();
	}

	@Override
	public void destroyPlugin() {
		shutdown();
	}
	
	private void logClientPorts() {
        // Log what ports the admin console is running on.
        String listening = "Client Server listening at";
        String hostname = getBindInterface() == null ?
                XMPPServer.getInstance().getServerInfo().getXMPPDomain() :
                getBindInterface();
        boolean isPlainStarted = false;

        for (Connector connector : clientServer.getConnectors()) {
            if (((ServerConnector) connector).getPort() == clientPort) {
                isPlainStarted = true;
            }
        }

        if (isPlainStarted) {
            log(listening + " http://" + hostname + ":" + clientPort);
        }
    }
}
