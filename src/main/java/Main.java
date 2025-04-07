import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import java.io.IOException;

public class Main implements BurpExtension {

    private static final String VERSION = "v1.0.0";

    public static MontoyaApi api;
    public static Config config;
    public static YascServer server;
    public static Collaborator collaborator;
    public static Script script;

    private Process scriptServerProcess;

    /**
     * Invoked when the extension is loaded. Any registered handlers will only be enabled once this method has completed.
     *
     * @param api The API implementation to access the functionality of Burp Suite.
     */
    @Override
    public void initialize(MontoyaApi api) {
        Main.api = api;
        api.extension().setName("Yasc");
        api.logging().logToOutput("Yasc " + VERSION + "\n");

        //        Load config file
        config = Config.load();

        api.http().registerHttpHandler(new YascHttpHandler());

//        Register context menu
        Menu.register();

        if (config.useCollaborator) {
            Main.collaborator = new Collaborator();
            api.logging().logToOutput("Collaborator payload: " + collaborator.getPayload());
        }

        Main.server = new YascServer();
        server.start();

        Main.script = new Script();

        var scriptServerCommand=System.getenv("YASC_SCRIPT_RUNNER_COMMAND");
        var scriptsDir=System.getenv("YASC_SCRIPTS_DIR");
        if (scriptServerCommand != null && scriptsDir != null) {
            launchScriptServer(scriptServerCommand, scriptsDir, server.getPort());
        }

        Main.api.extension().registerUnloadingHandler(() -> {
            try {
                server.stop();
            } catch (InterruptedException e) {
                Main.api.logging().logToError("error while unloading", e);
            }

            if (collaborator != null) {
                collaborator.stop();
            }

            if (scriptServerProcess != null) {
                scriptServerProcess.destroy();
            }

        });
    }

    void launchScriptServer(String scriptServerCommand, String scriptsDir, int yascServerPort){
//        Main.api.logging().logToOutput("scriptServerCommand: "+scriptServerCommand);
        try {

            ProcessBuilder p;
            if (System.getProperty("os.name").startsWith("Windows")){
                p=new ProcessBuilder("cmd","/c",scriptServerCommand);
            }else{
                p=new ProcessBuilder("sh","-c",scriptServerCommand);
            }

            var envMap=p.environment();
            envMap.put("YASC_SERVER_PORT", String.valueOf(yascServerPort));

            p.inheritIO();
            scriptServerProcess = p.start();

            Main.api.logging().logToOutput("Script runner started. pid: "+scriptServerProcess.pid());

            scriptServerProcess.onExit().thenAccept(process -> {
                Main.api.logging().logToOutput("Script runner exited with status "+process.exitValue());
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
