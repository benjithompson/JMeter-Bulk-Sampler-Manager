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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.TreeNode;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testelement.TestElement;

/**
 * Dialog for configuring bulk sampler operations.
 * Allows users to specify URI patterns and select actions (delete, disable, enable).
 * Includes a live preview of matching samplers.
 */
public class BulkSamplerDialog extends JDialog {

    /**
     * Enum representing the possible actions that can be performed on samplers.
     */
    public enum ActionType {
        DELETE("Delete", "Permanently remove matching samplers from the test plan"),
        DISABLE("Disable", "Disable matching samplers (they will not execute during test runs)"),
        ENABLE("Enable", "Enable matching samplers (previously disabled samplers will execute)");

        private final String displayName;
        private final String description;

        ActionType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        /**
         * Returns the display name of this action type.
         *
         * @return The display name
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns the description of this action type.
         *
         * @return The description
         */
        public String getDescription() {
            return description;
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
    private JCheckBox invertMatchCheckBox;
    private JList<String> previewList;
    private DefaultListModel<String> previewListModel;
    private JLabel matchCountLabel;
    private JLabel patternErrorLabel;
    private JLabel patternExampleLabel;
    private JLabel actionDescriptionLabel;
    private boolean confirmed = false;
    private Timer updateTimer;

    private static final String EXAMPLE_SIMPLE = "Example: api/users or login (matches if URI contains text)";
    private static final String EXAMPLE_REGEX = "Example: .*\\/api\\/.*  or  ^https://.*\\.com  (regex pattern)";

    /**
     * Creates a new BulkSamplerDialog.
     *
     * @param parent The parent frame
     */
    public BulkSamplerDialog(Frame parent) {
        super(parent, "Bulk Sampler Manager", true);
        initComponents();
        pack();
        setMinimumSize(new Dimension(600, 500));
        setLocationRelativeTo(parent);
        setupEscapeKey();
    }

    /**
     * Sets up the escape key to close the dialog.
     */
    private void setupEscapeKey() {
        getRootPane().registerKeyboardAction(
            e -> {
                confirmed = false;
                dispose();
            },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    /**
     * Initializes the dialog components.
     */
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        // Main panel with form fields
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Configuration panel
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Configuration",
            TitledBorder.LEFT, TitledBorder.TOP));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // URI Pattern
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 0;
        configPanel.add(new JLabel("URI Pattern:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        uriPatternField = new JTextField(30);
        uriPatternField.setToolTipText("Enter a pattern to match sampler URIs (e.g., '/api/users' or '.*\\.json')");
        configPanel.add(uriPatternField, gbc);

        // Pattern example label
        gbc.gridy = 1;
        gbc.gridx = 1;
        patternExampleLabel = new JLabel(EXAMPLE_SIMPLE);
        patternExampleLabel.setFont(patternExampleLabel.getFont().deriveFont(Font.ITALIC, 11f));
        patternExampleLabel.setForeground(Color.GRAY);
        configPanel.add(patternExampleLabel, gbc);

        // Pattern error label
        gbc.gridy = 2;
        gbc.gridx = 1;
        patternErrorLabel = new JLabel(" ");
        patternErrorLabel.setForeground(Color.RED);
        patternErrorLabel.setFont(patternErrorLabel.getFont().deriveFont(Font.ITALIC, 11f));
        configPanel.add(patternErrorLabel, gbc);

        // Action Type
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        configPanel.add(new JLabel("Action:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        actionComboBox = new JComboBox<>(ActionType.values());
        actionComboBox.setSelectedItem(ActionType.DISABLE);
        configPanel.add(actionComboBox, gbc);

        // Action description label
        gbc.gridy = 4;
        gbc.gridx = 1;
        actionDescriptionLabel = new JLabel(ActionType.DISABLE.getDescription());
        actionDescriptionLabel.setFont(actionDescriptionLabel.getFont().deriveFont(Font.ITALIC, 11f));
        actionDescriptionLabel.setForeground(Color.GRAY);
        configPanel.add(actionDescriptionLabel, gbc);

        // Options panel
        gbc.gridy = 5;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        useRegexCheckBox = new JCheckBox("Use Regular Expression");
        useRegexCheckBox.setToolTipText("Treat the pattern as a Java regular expression");
        caseSensitiveCheckBox = new JCheckBox("Case Sensitive");
        caseSensitiveCheckBox.setToolTipText("Match patterns with case sensitivity");
        invertMatchCheckBox = new JCheckBox("Invert Match");
        invertMatchCheckBox.setToolTipText("Apply action to samplers that do NOT match the pattern");
        optionsPanel.add(useRegexCheckBox);
        optionsPanel.add(caseSensitiveCheckBox);
        optionsPanel.add(invertMatchCheckBox);
        configPanel.add(optionsPanel, gbc);

        mainPanel.add(configPanel, BorderLayout.NORTH);

        // Preview panel
        JPanel previewPanel = new JPanel(new BorderLayout(5, 5));
        previewPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Matching Samplers Preview",
            TitledBorder.LEFT, TitledBorder.TOP));

        previewListModel = new DefaultListModel<>();
        previewList = new JList<>(previewListModel);
        previewList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        previewList.setVisibleRowCount(10);
        JScrollPane scrollPane = new JScrollPane(previewList);
        scrollPane.setPreferredSize(new Dimension(550, 200));
        previewPanel.add(scrollPane, BorderLayout.CENTER);

        matchCountLabel = new JLabel("Enter a pattern to see matching samplers");
        matchCountLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        previewPanel.add(matchCountLabel, BorderLayout.SOUTH);

        mainPanel.add(previewPanel, BorderLayout.CENTER);

        add(mainPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton previewButton = new JButton("Refresh Preview");
        JButton okButton = new JButton("Apply");
        JButton cancelButton = new JButton("Cancel");

        previewButton.addActionListener(e -> updatePreview());

        okButton.addActionListener(e -> {
            if (validateInput()) {
                confirmed = true;
                dispose();
            }
        });

        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });

        buttonPanel.add(previewButton);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Set default button
        getRootPane().setDefaultButton(okButton);

        // Setup listeners for live preview
        setupListeners();
    }

    /**
     * Sets up listeners for automatic preview updates.
     */
    private void setupListeners() {
        // Debounced update timer
        updateTimer = new Timer(300, e -> updatePreview());
        updateTimer.setRepeats(false);

        // Pattern field listener
        uriPatternField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { scheduleUpdate(); }
            @Override
            public void removeUpdate(DocumentEvent e) { scheduleUpdate(); }
            @Override
            public void changedUpdate(DocumentEvent e) { scheduleUpdate(); }
        });

        // Checkbox listeners
        useRegexCheckBox.addActionListener(e -> {
            // Update example text based on regex checkbox
            patternExampleLabel.setText(useRegexCheckBox.isSelected() ? EXAMPLE_REGEX : EXAMPLE_SIMPLE);
            updatePreview();
        });
        caseSensitiveCheckBox.addActionListener(e -> updatePreview());
        invertMatchCheckBox.addActionListener(e -> updatePreview());

        // Action combo listener
        actionComboBox.addActionListener(e -> {
            ActionType selected = (ActionType) actionComboBox.getSelectedItem();
            if (selected != null) {
                actionDescriptionLabel.setText(selected.getDescription());
            }
        });
    }

    /**
     * Schedules a preview update with debouncing.
     */
    private void scheduleUpdate() {
        if (updateTimer.isRunning()) {
            updateTimer.restart();
        } else {
            updateTimer.start();
        }
    }

    /**
     * Updates the preview list with matching samplers.
     */
    private void updatePreview() {
        previewListModel.clear();
        patternErrorLabel.setText(" ");

        String pattern = uriPatternField.getText().trim();
        if (pattern.isEmpty()) {
            matchCountLabel.setText("Enter a pattern to see matching samplers");
            return;
        }

        // Validate regex if needed
        if (useRegexCheckBox.isSelected()) {
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                patternErrorLabel.setText("Invalid regex: " + e.getDescription());
                matchCountLabel.setText("Fix the pattern error above");
                return;
            }
        }

        // Find matching samplers
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null || guiPackage.getTreeModel() == null) {
            matchCountLabel.setText("Unable to access test plan");
            return;
        }

        List<String> matches = findMatchingSamplerNames(guiPackage, pattern, 
            useRegexCheckBox.isSelected(), caseSensitiveCheckBox.isSelected(),
            invertMatchCheckBox.isSelected());

        for (String match : matches) {
            previewListModel.addElement(match);
        }

        if (matches.isEmpty()) {
            matchCountLabel.setText("No samplers match the pattern");
        } else {
            matchCountLabel.setText(String.format("Found %d matching sampler(s)", matches.size()));
        }
    }

    /**
     * Finds sampler names matching the pattern.
     */
    private List<String> findMatchingSamplerNames(GuiPackage guiPackage, String uriPattern,
            boolean useRegex, boolean caseSensitive, boolean invertMatch) {
        
        List<String> results = new ArrayList<>();
        JMeterTreeNode rootNode = (JMeterTreeNode) guiPackage.getTreeModel().getRoot();
        
        Pattern pattern = null;
        if (useRegex) {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            pattern = Pattern.compile(uriPattern, flags);
        }

        findMatchingSamplersRecursive(rootNode, uriPattern, pattern, caseSensitive, invertMatch, results);
        return results;
    }

    /**
     * Recursively finds matching samplers.
     */
    private void findMatchingSamplersRecursive(JMeterTreeNode node, String uriPattern,
            Pattern pattern, boolean caseSensitive, boolean invertMatch, List<String> results) {
        
        TestElement element = node.getTestElement();
        
        if (element instanceof Sampler) {
            String uri = extractUri(element);
            if (uri != null) {
                boolean matches = matchesPattern(uri, uriPattern, pattern, caseSensitive);
                // Invert the match result if invertMatch is enabled
                if (invertMatch) {
                    matches = !matches;
                }
                if (matches) {
                    String status = element.isEnabled() ? "" : " [DISABLED]";
                    results.add(element.getName() + " → " + uri + status);
                }
            }
        }

        Enumeration<TreeNode> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            findMatchingSamplersRecursive(child, uriPattern, pattern, caseSensitive, invertMatch, results);
        }
    }

    /**
     * Extracts the URI from a sampler element.
     * For HTTP samplers, builds full URL from protocol/domain/port/path.
     * Also includes the sampler name in the searchable text.
     */
    private String extractUri(TestElement element) {
        StringBuilder searchableText = new StringBuilder();
        
        // Always include the element name (it might contain the URL)
        String name = element.getName();
        if (name != null) {
            searchableText.append(name);
        }
        
        if (element instanceof HTTPSamplerBase httpSampler) {
            String path = httpSampler.getPath();
            String domain = httpSampler.getDomain();
            
            // Build full URI if we have domain info
            if (domain != null && !domain.isEmpty()) {
                String protocol = httpSampler.getProtocol();
                if (protocol == null || protocol.isEmpty()) {
                    protocol = "http";
                }
                int port = httpSampler.getPort();
                StringBuilder uri = new StringBuilder();
                uri.append(protocol).append("://").append(domain);
                if (port > 0 && port != 80 && port != 443) {
                    uri.append(":").append(port);
                }
                if (path != null && !path.isEmpty()) {
                    if (!path.startsWith("/")) {
                        uri.append("/");
                    }
                    uri.append(path);
                }
                // Append constructed URI if different from name
                String constructedUri = uri.toString();
                if (!constructedUri.equals(name)) {
                    searchableText.append(" ").append(constructedUri);
                }
            } else if (path != null && !path.isEmpty() && !path.equals(name)) {
                // Just append path if no domain but path exists
                searchableText.append(" ").append(path);
            }
        }
        
        return searchableText.toString();
    }

    /**
     * Checks if a URI matches the pattern.
     */
    private boolean matchesPattern(String uri, String uriPattern, Pattern pattern, boolean caseSensitive) {
        if (uri == null) {
            return false;
        }
        
        if (pattern != null) {
            return pattern.matcher(uri).find();
        } else {
            if (caseSensitive) {
                return uri.contains(uriPattern);
            } else {
                return uri.toLowerCase().contains(uriPattern.toLowerCase());
            }
        }
    }

    /**
     * Validates the input before applying.
     */
    private boolean validateInput() {
        String pattern = uriPatternField.getText().trim();
        
        if (pattern.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter a URI pattern.",
                "Validation Error",
                JOptionPane.WARNING_MESSAGE);
            uriPatternField.requestFocus();
            return false;
        }

        if (useRegexCheckBox.isSelected()) {
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                JOptionPane.showMessageDialog(this,
                    "Invalid regular expression: " + e.getMessage(),
                    "Pattern Error",
                    JOptionPane.ERROR_MESSAGE);
                uriPatternField.requestFocus();
                return false;
            }
        }

        // Confirm destructive action
        if (actionComboBox.getSelectedItem() == ActionType.DELETE) {
            int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to DELETE matching samplers?\n" +
                "This action cannot be undone.",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            return result == JOptionPane.YES_OPTION;
        }

        return true;
    }

    /**
     * Returns whether the user confirmed the dialog.
     *
     * @return true if the user clicked Apply, false if cancelled
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
        return uriPatternField.getText().trim();
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

    /**
     * Returns whether matching should be inverted.
     *
     * @return true if action should apply to non-matching samplers
     */
    public boolean isInvertMatch() {
        return invertMatchCheckBox.isSelected();
    }
}
