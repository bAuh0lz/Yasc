import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.collaborator.InteractionType;
import burp.api.montoya.collaborator.SecretKey;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Collaborator {
    private final CollaboratorClient client;
    private final CollaboratorPayload payload;
    private final SecretKey clientKey;
    private final Timer timer;

    public Collaborator() {
        client = Main.api.collaborator().createClient();
        clientKey = client.getSecretKey();
        payload = client.generatePayload();

        timer = new Timer();
        TimerTask task = new CollaboratorKeeper();
        timer.schedule(task, 0, 1000 * 60 * 10);
    }

    public List<String> fetchSmtpMessages() {
        var smtpInters = client.getInteractions((server, interaction) -> interaction.type() == InteractionType.SMTP);

        return smtpInters.stream().map(interaction -> interaction.smtpDetails().get().conversation()).toList();
    }

    public CollaboratorPayload getPayload() {
        return payload;
    }

    public void stop() {
        timer.cancel();
    }

    class CollaboratorKeeper extends TimerTask {
        public void run() {
            var dnsInters = client.getInteractions((server, interaction) -> interaction.type() == InteractionType.DNS);
//            Main.api.logging().logToOutput(now+" DNS Size: "+dnsInters.size());
        }
    }
}
