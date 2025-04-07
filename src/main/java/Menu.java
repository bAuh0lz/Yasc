import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Menu implements ContextMenuItemsProvider {
    public static void register() {
        Main.api.userInterface().registerContextMenuItemsProvider(new Menu());
    }

    /**
     * Invoked by Burp Suite when the user requests a context menu with HTTP request/response information in the user interface.
     * Extensions should return {@code null} or {@link Collections#emptyList()} from this method, to indicate that no menu items are required.
     *
     * @param event This object can be queried to find out about HTTP request/responses that are associated with the context menu invocation.
     * @return A list of custom menu items (which may include sub-menus, checkbox menu items, etc.) that should be displayed.
     */
    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        var topMenus = new ArrayList<Component>();

        var selectedRequestResponseOptional = event.messageEditorRequestResponse();
        if (selectedRequestResponseOptional.isEmpty()) {
            return null;
        }

        var selectedRequestResponse = selectedRequestResponseOptional.get();

        var selectScriptMenu = new JMenu("Select script");
        var selectScriptMenuItem = getSelectScriptMenuItem("Pre script", selectedRequestResponse, false);
        selectScriptMenu.add(selectScriptMenuItem);

        var selectPostScriptMenuItem = getSelectScriptMenuItem("Post script", selectedRequestResponse, true);
        selectScriptMenu.add(selectPostScriptMenuItem);

        topMenus.add(selectScriptMenu);


        var copyRequestMenuItem = getCopyRequestMenuItem("Copy request", selectedRequestResponse);
        topMenus.add(copyRequestMenuItem);

        var disableMenuItem = new JMenu("Disable script");

        var disableScriptMenuItem = getDisalbeScriptMenuItem("Pre script", selectedRequestResponse, false);
        disableMenuItem.add(disableScriptMenuItem);

        var disablePostScriptMenuItem = getDisalbeScriptMenuItem("Post script", selectedRequestResponse, true);
        disableMenuItem.add(disablePostScriptMenuItem);

        topMenus.add(disableMenuItem);

        return topMenus;
    }

    private static JMenuItem getSelectScriptMenuItem(String label, MessageEditorHttpRequestResponse messageEditorHttpRequestResponse, boolean isPostScript) {
        var jMenuItem = new JMenuItem(label);
        jMenuItem.addActionListener(
                actionEvent -> {
                    var selectedFilename = Main.script.selectFile();
                    if (selectedFilename == null) {
                        return;
                    }

                    var paramName = isPostScript ? Main.config.postScriptParameterName : Main.config.preScriptParameterName;
                    var paramType = isPostScript ? Main.config.postScriptParameterType : Main.config.preScriptParameterType;


                    var scriptParameter = HttpParameter.parameter(paramName, selectedFilename, paramType);
                    var updatedRequest = messageEditorHttpRequestResponse.requestResponse().request().withParameter(scriptParameter);
                    messageEditorHttpRequestResponse.setRequest(updatedRequest);
                });
        return jMenuItem;
    }

    private static JMenuItem getCopyRequestMenuItem(String label, MessageEditorHttpRequestResponse messageEditorHttpRequestResponse) {
        var jMenuItem = new JMenuItem(label);
        jMenuItem.addActionListener(
                actionEvent -> {
                    var req = messageEditorHttpRequestResponse.requestResponse().request();

                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");
                    String fileName = LocalDateTime.now().format(formatter) + ".http";

                    var filePath = Main.script.writeRequestFile(fileName, req.toByteArray().getBytes());

                    var copyString = Main.config.copiedCodeFormat
                            .replace("$URL", req.url())
                            .replace("$BASE_URL", req.httpService().toString())
                            .replace("$METHOD", req.method())
                            .replace("$REQUEST_FILE_PATH", filePath.toString())
                            .replace("$REQUEST_FILE_NAME", filePath.getFileName().toString());

                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(copyString), new _ClipboardOwner());


                });
        return jMenuItem;
    }

    private static JMenuItem getDisalbeScriptMenuItem(String label, MessageEditorHttpRequestResponse messageEditorHttpRequestResponse, boolean isPostScript) {
        var jMenuItem = new JMenuItem(label);
        jMenuItem.addActionListener(
                actionEvent -> {

                    var paramName = isPostScript ? Main.config.disablePostScriptParameterName : Main.config.disablePreScriptParameterName;
                    var paramType = isPostScript ? Main.config.disablePostScriptParameterType : Main.config.disablePreScriptParameterType;

                    var disableScriptParameter = HttpParameter.parameter(paramName, "", paramType);
                    var updatedRequest = messageEditorHttpRequestResponse.requestResponse().request().withParameter(disableScriptParameter);
                    messageEditorHttpRequestResponse.setRequest(updatedRequest);
                });
        return jMenuItem;
    }

    static class _ClipboardOwner implements ClipboardOwner {

        @Override
        public void lostOwnership(Clipboard clipboard, Transferable transferable) {

        }
    }
}
