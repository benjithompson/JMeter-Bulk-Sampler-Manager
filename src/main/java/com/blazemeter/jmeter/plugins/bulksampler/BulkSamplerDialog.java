/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blazemeter.jmeter.plugins.bulksampler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Dialog for configuring bulk sampler operations.
 * Allows users to specify URI patterns and select actions (delete, disable, enable).
 */
public class BulkSamplerDialog extends JDialog {

    /**
     * Enum representing the possible actions that can be performed on samplers.
     */
    public enum ActionType {
        DELETE("Delete"),
        DISABLE("Disable"),
        ENABLE("Enable");

        private final String displayName;

        ActionType(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Returns the display name of this action type.
         *
         * @return The display name
         */
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private JTextField uriPatternField;
    private JComboBox<ActionType> actionComboBox;
    private JCheckBox useRegexCheckBox;
    private JCheckBox caseSensitiveCheckBox;
    private boolean confirmed = false;

    /**
     * Creates a new BulkSamplerDialog.
     *
     * @param parent The parent frame
     */
    public BulkSamplerDialog(Frame parent) {
        super(parent, "Bulk Sampler Operations", true);
        initComponents();
        pack();
        setLocationRelativeTo(parent);
    }

    /**
     * Initializes the dialog components.
     */
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        // Main panel with form fields
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // URI Pattern
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(new JLabel("URI Pattern:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        uriPatternField = new JTextField(30);
        mainPanel.add(uriPatternField, gbc);

        // Action Type
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        mainPanel.add(new JLabel("Action:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        actionComboBox = new JComboBox<>(ActionType.values());
        mainPanel.add(actionComboBox, gbc);

        // Use Regex checkbox
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        useRegexCheckBox = new JCheckBox("Use Regular Expression");
        mainPanel.add(useRegexCheckBox, gbc);

        // Case Sensitive checkbox
        gbc.gridy = 3;
        caseSensitiveCheckBox = new JCheckBox("Case Sensitive");
        mainPanel.add(caseSensitiveCheckBox, gbc);

        add(mainPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");

        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                confirmed = true;
                dispose();
            }
        });

        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                confirmed = false;
                dispose();
            }
        });

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Set default button
        getRootPane().setDefaultButton(okButton);
    }

    /**
     * Returns whether the user confirmed the dialog.
     *
     * @return true if the user clicked OK, false if cancelled
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Returns the URI pattern entered by the user.
     *
     * @return The URI pattern string
     */
    public String getUriPattern() {
        return uriPatternField.getText();
    }

    /**
     * Returns the selected action type.
     *
     * @return The selected ActionType
     */
    public ActionType getSelectedAction() {
        return (ActionType) actionComboBox.getSelectedItem();
    }

    /**
     * Returns whether regex matching should be used.
     *
     * @return true if regex should be used
     */
    public boolean isUseRegex() {
        return useRegexCheckBox.isSelected();
    }

    /**
     * Returns whether matching should be case-sensitive.
     *
     * @return true if case-sensitive matching should be used
     */
    public boolean isCaseSensitive() {
        return caseSensitiveCheckBox.isSelected();
    }
}
