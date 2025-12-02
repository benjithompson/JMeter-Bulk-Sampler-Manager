# JMeter Bulk Sampler Manager Plugin

A JMeter plugin that allows you to bulk delete, disable, or enable samplers based on URI patterns. This is useful for quickly modifying large test plans with many HTTP samplers.

## Features

- **Bulk Delete**: Permanently remove samplers matching a URI pattern
- **Bulk Disable**: Disable samplers matching a URI pattern (they won't execute during test runs)
- **Bulk Enable**: Re-enable previously disabled samplers matching a URI pattern
- **Pattern Matching**: Support for both simple text matching and regular expressions
- **Case Sensitivity**: Option to enable case-sensitive pattern matching
- **Live Preview**: See matching samplers before applying changes
- **HTTP Sampler Support**: Full support for HTTP Request samplers with domain, port, and path matching

## Installation

1. Build the plugin:
   ```bash
   mvn clean package
   ```

2. Copy the generated JAR file from `target/bulk-sampler-manager-1.0.0.jar` to your JMeter `lib/ext` directory:
   ```bash
   cp target/bulk-sampler-manager-1.0.0.jar $JMETER_HOME/lib/ext/
   ```

3. Restart JMeter

## Usage

1. Open JMeter and load your test plan
2. Go to **Edit** menu and select **Bulk Sampler Manager...**
3. In the dialog:
   - Enter a **URI Pattern** to match (e.g., `/api/users` or `.*\.json`)
   - Select an **Action**: Delete, Disable, or Enable
   - Optionally enable **Use Regular Expression** for regex patterns
   - Optionally enable **Case Sensitive** matching
4. The **Matching Samplers Preview** shows which samplers will be affected
5. Click **Apply** to perform the action

## Pattern Matching Examples

### Simple Text Matching

| Pattern | Matches |
|---------|---------|
| `/api/` | Any URI containing `/api/` |
| `users` | Any URI containing `users` |
| `.json` | Any URI containing `.json` |

### Regular Expression Matching

| Pattern | Matches |
|---------|---------|
| `^/api/v[0-9]+/` | URIs starting with `/api/v1/`, `/api/v2/`, etc. |
| `.*\.(jpg\|png\|gif)$` | URIs ending with `.jpg`, `.png`, or `.gif` |
| `^https://.*\.example\.com` | Full URIs using HTTPS on example.com subdomains |
| `/users/[0-9]+$` | URIs ending with `/users/` followed by a number |

## Requirements

- Apache JMeter 5.6.3 or later
- Java 21 or later

## Building from Source

```bash
# Clone the repository
git clone <repository-url>
cd BulkDeleteSamplers

# Build with Maven
mvn clean package

# The JAR will be in target/bulk-sampler-manager-1.0.0.jar
```

## Project Structure

```
src/main/java/com/blazemeter/jmeter/plugins/bulksampler/
├── BulkSamplerAction.java       # Main plugin action (implements Command)
├── BulkSamplerDialog.java       # Configuration dialog with live preview
└── BulkSamplerMenuCreator.java  # Menu integration (implements MenuCreator)

src/main/resources/META-INF/services/
├── org.apache.jmeter.gui.action.Command      # Service provider for Command
└── org.apache.jmeter.gui.plugin.MenuCreator  # Service provider for MenuCreator
```

## License

Licensed under the Apache License, Version 2.0.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
