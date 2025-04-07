import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.google.protobuf.ByteString;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import script.RunScriptReply;
import script.RunScriptRequest;
import script.ScriptGrpc;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Script {
    private static Path scriptDirectory;
    private static Path requestDirectory;

    private ScriptGrpc.ScriptBlockingStub blockingStub;

    public Boolean prepare(int port) {
        if (blockingStub != null) {
            var opt = JOptionPane.showConfirmDialog(null,"Are you sure you want to override script server's port?","Yasc",JOptionPane.YES_NO_OPTION);
            if (opt==JOptionPane.NO_OPTION){
                return false;
            }
        }


        String target = "127.0.0.1:" + port;
        ManagedChannel channel = Grpc.newChannelBuilder(target, InsecureChannelCredentials.create()).build();

        blockingStub = ScriptGrpc.newBlockingStub(channel);
        return true;
    }

    public void setScriptDirectory(String scriptDir){
        scriptDirectory=Path.of(scriptDir);
        requestDirectory=scriptDirectory.resolve(Main.config.exportedRequestDirName);
        requestDirectory.toFile().mkdir();
    }

    public String selectFile() {
        var fileChooserBase = "";
        if (scriptDirectory != null) {
            fileChooserBase = Script.scriptDirectory.toAbsolutePath().toString();
        }

        var chooser = new JFileChooser(fileChooserBase);
        chooser.setDialogTitle("Select script file.");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        var result = chooser.showOpenDialog(null);

        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        Script.scriptDirectory = chooser.getSelectedFile().toPath().getParent();
        Script.requestDirectory = scriptDirectory.resolve(Main.config.exportedRequestDirName);
        requestDirectory.toFile().mkdir();

        return chooser.getSelectedFile().getName();
    }

    public Path writeRequestFile(String filename, byte[] request) {
        if (requestDirectory == null) {
            selectFile();
        }

        var filePath = requestDirectory.resolve(filename);

        try {
            Files.write(filePath, request);
        } catch (IOException e) {
            Main.api.logging().logToError(e);
        }

        return filePath;
    }

    public byte[] run(String scriptName, HttpRequest httpRequest, HttpResponse httpResponse, boolean isPostScript) throws RuntimeException {
        var scriptPath = scriptDirectory.resolve(scriptName).normalize();

        var runScriptRequestBuilder = RunScriptRequest.newBuilder()
                .setScriptPath(scriptPath.toString())
                .setIsPostScript(isPostScript);

        if (httpRequest != null) {
            runScriptRequestBuilder.setHttpRequest(ByteString.copyFrom(httpRequest.toByteArray().getBytes()));
        }

        if (httpResponse != null) {
            runScriptRequestBuilder.setHttpResponse(ByteString.copyFrom(httpResponse.toByteArray().getBytes()));
        }

        var runScriptRequest = runScriptRequestBuilder.build();

        RunScriptReply reply;
        try {
            reply = blockingStub.runScript(runScriptRequest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (!reply.getError().isEmpty()) {
            throw new RuntimeException(reply.getError());
        }

        if (reply.getModifiedRequestToBeSent().isEmpty()) {
            return null;
        }

        return reply.getModifiedRequestToBeSent().toByteArray();
    }

}
