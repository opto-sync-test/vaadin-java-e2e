package dev.optosync.test;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.zedpkg.opto_sync.OptoSyncClient;
import java.net.URI;

@Route("")
@PageTitle("Vaadin OptoSync E2E")
@JavaScript("./opto-sync-register.js")
public class MainView extends VerticalLayout {
    public MainView() {
        var client = new OptoSyncClient(URI.create("/opto-sync"), null);
        add(new H1("Vaadin + OptoSync"));
        add(new Paragraph(new Text(
                "Pinned Java client endpoint: " + client.baseUri() +
                        "; durable browser and virtual-thread workers enabled.")));
    }
}
