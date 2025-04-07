import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.utilities.json.JsonNode;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Config {
    public List<ToolType> targetToolType;

    public String exportedRequestDirName;
    public String copiedCodeFormat;

    public String preScriptParameterName;
    public HttpParameterType preScriptParameterType;
    public String postScriptParameterName;
    public HttpParameterType postScriptParameterType;

    public String disablePreScriptParameterName;
    public HttpParameterType disablePreScriptParameterType;
    public String disablePostScriptParameterName;
    public HttpParameterType disablePostScriptParameterType;

    public Boolean useCollaborator;

    public Boolean cancelRequestOnScriptError;
    public int cancelRequestPort;


    public Config(Path settingsFilePath) {
        try {
            var extensionSettingsContents = Files.readString(settingsFilePath);
            parse(extensionSettingsContents);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Config load() {
        var envConfigPathString =System.getenv("YASC_CONFIG_FILE");
        if (envConfigPathString != null) {
            var configPath = Paths.get(envConfigPathString);
            if (configPath.toFile().exists()) {
                var config = new Config(configPath);
                Main.api.logging().logToOutput("Loaded config file: "+configPath+"\n");
                return config;
            }
        }

        var configPath =  Paths.get(System.getProperty("user.home"), ".yasc", "config.json");
        if (configPath.toFile().exists()) {
            var config = new Config(configPath);
            Main.api.logging().logToOutput("Loaded config file: "+configPath+"\n");
            return config;
        }

        StringBuilder data= new StringBuilder();
        try (InputStream in = Config.class.getResourceAsStream("/config.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            // Use resource
            String tmp;
            while ((tmp= reader.readLine())!=null){
                data.append(tmp);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var config = new Config(data.toString());

        Main.api.logging().logToOutput("Default config file loaded.");
        Main.api.logging().logToOutput("If necessary, put your custom config file as " + configPath + "\n");

        return config;
    }

    public Config(String extensionSettingsContents) {
        parse(extensionSettingsContents);
    }

    private void parse(String extensionSettingsContents) {
        var extensionSettings = JsonNode.jsonNode(extensionSettingsContents).asObject();

        targetToolType = extensionSettings.get("targetToolType").asArray().asList().stream().map(jsonNode -> ToolType.valueOf(jsonNode.asString())).toList();

        copiedCodeFormat = extensionSettings.getString("copiedCodeFormat");

        preScriptParameterName = extensionSettings.getString("preScriptParameterName");

        var preScriptParameterTypeString = extensionSettings.getString("preScriptParameterType");
        preScriptParameterType = HttpParameterType.valueOf(preScriptParameterTypeString);

        postScriptParameterName = extensionSettings.getString("postScriptParameterName");

        var postScriptParameterTypeString = extensionSettings.getString("postScriptParameterType");
        postScriptParameterType = HttpParameterType.valueOf(postScriptParameterTypeString);

        useCollaborator = extensionSettings.getBoolean("useCollaborator");

        cancelRequestOnScriptError = extensionSettings.getBoolean("cancelRequestOnScriptError");
        cancelRequestPort = extensionSettings.getNumber("cancelRequestPort").intValue();

        exportedRequestDirName = extensionSettings.getString("exportedRequestDirName");

        disablePreScriptParameterName = extensionSettings.getString("disablePreScriptParameterName");

        var disablePreScriptParameterTypeString = extensionSettings.getString("disablePreScriptParameterType");
        disablePreScriptParameterType = HttpParameterType.valueOf(disablePreScriptParameterTypeString);

        disablePostScriptParameterName = extensionSettings.getString("disablePostScriptParameterName");

        var disablePostScriptParameterTypeString = extensionSettings.getString("disablePostScriptParameterType");
        disablePostScriptParameterType = HttpParameterType.valueOf(disablePostScriptParameterTypeString);
    }
}
