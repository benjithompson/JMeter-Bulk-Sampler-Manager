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

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.action.AbstractAction;
import org.apache.jmeter.gui.action.ActionNames;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testelement.TestElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * JMeter plugin action for bulk delete or disable of samplers based on URI patterns.
 * This action can be accessed from the JMeter Edit menu.
 * 
 * <p>The plugin allows users to:
 * <ul>
 *   <li>Delete samplers matching a URI pattern</li>
 *   <li>Disable samplers matching a URI pattern</li>
 *   <li>Enable previously disabled samplers matching a URI pattern</li>
 * </ul>
 * 
 * <p>Patterns can be simple text matches or regular expressions.
 */
public class BulkSamplerAction extends AbstractAction {

    private static final Logger log = LoggerFactory.getLogger(BulkSamplerAction.class);

    /** Action command name for this plugin */
    public static final String BULK_SAMPLER_MANAGER = "bulkSamplerManager";

    /** Set of action names this command responds to */
    private static final Set<String> commands = new HashSet<>();

    static {
        commands.add(BULK_SAMPLER_MANAGER);
    }

    /**
     * Default constructor required by JMeter.
     */
    public BulkSamplerAction() {
        super();
    }

    /**
     * Returns the set of action commands this action responds to.
     * 
     * @return Set of action command strings
     */
    @Override
    public Set<String> getActionNames() {
        return commands;
    }

    /**
     * Performs the bulk sampler management action.
     * Opens a dialog for user input and processes samplers based on the configuration.
     * 
     * @param e The action event that triggered this action
     */
    @Override
    public void doAction(ActionEvent e) {
        log.debug("BulkSamplerAction triggered");

        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null) {
            log.error("GuiPackage is null - cannot perform action");
            return;
        }

        // Show the configuration dialog
        BulkSamplerDialog dialog = new BulkSamplerDialog(guiPackage.getMainFrame());
        dialog.setVisible(true);

        if (!dialog.isConfirmed()) {
            log.debug("User cancelled the operation");
            return;
        }

        // Get configuration from dialog
        String uriPattern = dialog.getUriPattern();
        BulkSamplerDialog.ActionType actionType = dialog.getSelectedAction();
        boolean useRegex = dialog.isUseRegex();
        boolean caseSensitive = dialog.isCaseSensitive();

        if (uriPattern == null || uriPattern.trim().isEmpty()) {
            JOptionPane.showMessageDialog(
                guiPackage.getMainFrame(),
                "Please enter a URI pattern.",
                "Invalid Input",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        // Perform the action
        try {
            int affectedCount = processTestPlan(guiPackage, uriPattern, actionType, useRegex, caseSensitive);
            
            // Refresh the tree to show changes
            guiPackage.getTreeModel().reload();
            guiPackage.refreshCurrentGui();

            // Show result message
            String actionName = actionType.getDisplayName().toLowerCase();
            JOptionPane.showMessageDialog(
                guiPackage.getMainFrame(),
                "Successfully %s %d sampler(s) matching pattern: %s".formatted(
                    actionName, affectedCount, uriPattern),
                "Bulk Sampler Manager",
                JOptionPane.INFORMATION_MESSAGE
            );

            log.info("Bulk sampler action completed: {} {} sampler(s) matching '{}'", 
                actionName, affectedCount, uriPattern);

        } catch (PatternSyntaxException ex) {
            log.error("Invalid regex pattern: {}", uriPattern, ex);
            JOptionPane.showMessageDialog(
                guiPackage.getMainFrame(),
                "Invalid regular expression: " + ex.getMessage(),
                "Pattern Error",
                JOptionPane.ERROR_MESSAGE
            );
        } catch (Exception ex) {
            log.error("Error processing samplers", ex);
            JOptionPane.showMessageDialog(
                guiPackage.getMainFrame(),
                "Error processing samplers: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Processes the test plan to find and modify samplers matching the pattern.
     * 
     * @param guiPackage The JMeter GUI package
     * @param uriPattern The pattern to match against sampler URIs
     * @param actionType The action to perform (delete, disable, enable)
     * @param useRegex Whether to treat the pattern as a regular expression
     * @param caseSensitive Whether matching should be case-sensitive
     * @return The number of samplers affected
     * @throws PatternSyntaxException if the regex pattern is invalid
     */
    private int processTestPlan(GuiPackage guiPackage, String uriPattern, 
            BulkSamplerDialog.ActionType actionType, boolean useRegex, boolean caseSensitive) {
        
        JMeterTreeNode rootNode = (JMeterTreeNode) guiPackage.getTreeModel().getRoot();
        List<JMeterTreeNode> matchingSamplers = new ArrayList<>();
        
        // Compile pattern
        Pattern pattern = null;
        if (useRegex) {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            pattern = Pattern.compile(uriPattern, flags);
        }

        // Find all matching samplers
        findMatchingSamplers(rootNode, uriPattern, pattern, caseSensitive, matchingSamplers);

        log.debug("Found {} samplers matching pattern '{}'", matchingSamplers.size(), uriPattern);

        // Process samplers based on action type
        int affectedCount = 0;
        
        // For delete action, we need to process in reverse order to avoid index issues
        if (actionType == BulkSamplerDialog.ActionType.DELETE) {
            Collections.reverse(matchingSamplers);
        }

        for (JMeterTreeNode node : matchingSamplers) {
            boolean success = false;
            switch (actionType) {
                case DELETE:
                    success = deleteSampler(guiPackage, node);
                    break;
                case DISABLE:
                    success = setEnabled(node, false);
                    break;
                case ENABLE:
                    success = setEnabled(node, true);
                    break;
            }
            if (success) {
                affectedCount++;
            }
        }

        return affectedCount;
    }

    /**
     * Recursively finds all samplers matching the given URI pattern.
     * 
     * @param node The current node to examine
     * @param uriPattern The pattern string (for simple matching)
     * @param pattern The compiled regex pattern (null for simple matching)
     * @param caseSensitive Whether matching should be case-sensitive
     * @param matchingSamplers List to add matching sampler nodes to
     */
    private void findMatchingSamplers(JMeterTreeNode node, String uriPattern, 
            Pattern pattern, boolean caseSensitive, List<JMeterTreeNode> matchingSamplers) {
        
        TestElement element = node.getTestElement();
        
        // Check if this is a sampler
        if (element instanceof Sampler) {
            String uri = extractUri(element);
            if (uri != null && matchesPattern(uri, uriPattern, pattern, caseSensitive)) {
                matchingSamplers.add(node);
                log.debug("Found matching sampler: {} with URI: {}", element.getName(), uri);
            }
        }

        // Recursively process children
        Enumeration<TreeNode> children = node.children();
        while (children.hasMoreElements()) {
            JMeterTreeNode child = (JMeterTreeNode) children.nextElement();
            findMatchingSamplers(child, uriPattern, pattern, caseSensitive, matchingSamplers);
        }
    }

    /**
     * Extracts the URI from a test element (sampler).
     * Supports HTTP samplers and falls back to element name for other types.
     * 
     * @param element The test element to extract URI from
     * @return The extracted URI or element name
     */
    private String extractUri(TestElement element) {
        if (element instanceof HTTPSamplerBase httpSampler) {
            String path = httpSampler.getPath();
            String domain = httpSampler.getDomain();
            
            // Build full URI if domain is available
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
                return uri.toString();
            }
            
            // Return just the path if no domain
            return path != null ? path : element.getName();
        }
        
        // For non-HTTP samplers, use the element name
        return element.getName();
    }

    /**
     * Checks if a URI matches the given pattern.
     * 
     * @param uri The URI to check
     * @param uriPattern The pattern string (for simple matching)
     * @param pattern The compiled regex pattern (null for simple matching)
     * @param caseSensitive Whether matching should be case-sensitive
     * @return true if the URI matches the pattern
     */
    private boolean matchesPattern(String uri, String uriPattern, Pattern pattern, boolean caseSensitive) {
        if (uri == null) {
            return false;
        }
        
        if (pattern != null) {
            // Regex matching
            return pattern.matcher(uri).find();
        } else {
            // Simple contains matching
            if (caseSensitive) {
                return uri.contains(uriPattern);
            } else {
                return uri.toLowerCase().contains(uriPattern.toLowerCase());
            }
        }
    }

    /**
     * Deletes a sampler node from the test plan tree.
     * 
     * @param guiPackage The JMeter GUI package
     * @param node The node to delete
     * @return true if deletion was successful
     */
    private boolean deleteSampler(GuiPackage guiPackage, JMeterTreeNode node) {
        try {
            guiPackage.getTreeModel().removeNodeFromParent(node);
            log.debug("Deleted sampler: {}", node.getName());
            return true;
        } catch (Exception e) {
            log.error("Failed to delete sampler: {}", node.getName(), e);
            return false;
        }
    }

    /**
     * Enables or disables a sampler.
     * 
     * @param node The sampler node to modify
     * @param enabled Whether to enable (true) or disable (false) the sampler
     * @return true if the operation was successful
     */
    private boolean setEnabled(JMeterTreeNode node, boolean enabled) {
        try {
            TestElement element = node.getTestElement();
            element.setEnabled(enabled);
            log.debug("{} sampler: {}", enabled ? "Enabled" : "Disabled", node.getName());
            return true;
        } catch (Exception e) {
            log.error("Failed to {} sampler: {}", enabled ? "enable" : "disable", node.getName(), e);
            return false;
        }
    }
}
