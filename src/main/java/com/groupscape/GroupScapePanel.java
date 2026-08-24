package com.groupscape;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import net.runelite.client.ui.PluginPanel;

class GroupScapePanel extends PluginPanel {
    GroupScapePanel(Runnable onOpenGroupScape) {
        super(false);

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton openButton = new JButton("Open GroupScape");
        openButton.setPreferredSize(new Dimension(0, 30));
        openButton.addActionListener(event -> onOpenGroupScape.run());
        add(openButton, BorderLayout.NORTH);
    }
}
