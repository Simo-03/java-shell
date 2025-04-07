import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Console;
import java.util.Collections;

public class Main {
    private static File currentDirectory = new File(System.getProperty("user.dir"));

    public static void main(String[] args) throws Exception {
        // Setup built-in commands
        ArrayList<String> shellCommands = new ArrayList<String>();
        shellCommands.add("echo");
        shellCommands.add("exit");
        shellCommands.add("type");
        shellCommands.add("pwd");
        shellCommands.add("cd");

        ProcessBuilder termPb = new ProcessBuilder("/bin/bash", "-c", "stty -echo -icanon min 1");
        termPb.inheritIO();
        Process rawMode = termPb.start();
        rawMode.waitFor();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        boolean running = true;

        try {
            while (running) {
                System.out.print("$ ");
                System.out.flush();

                StringBuilder buffer = new StringBuilder();
                int c;
                boolean escapeMode = false;

                // Track for double tab
                boolean lastKeyWasTab = false;
                List<String> tabCompletionMatches = new ArrayList<>();

                while ((c = reader.read()) != '\n') {
                    if (c == '\t') { // tab key
                        String input = buffer.toString().trim();

                        if (lastKeyWasTab && !tabCompletionMatches.isEmpty()) {
                            // Sort alphabetically
                            Collections.sort(tabCompletionMatches);

                            System.out.println();
                            for (int i = 0; i < tabCompletionMatches.size(); i++) {
                                System.out.print(tabCompletionMatches.get(i));
                                if (i < tabCompletionMatches.size() - 1) {
                                    System.out.print("  ");
                                }
                            }
                            System.out.println();
                            System.out.print("$ " + buffer.toString());
                            System.out.flush();
                            tabCompletionMatches.clear();
                            lastKeyWasTab = false;
                            continue;
                        }

                        // Reset matches
                        tabCompletionMatches.clear();
                        boolean foundMatch = false;
                        String exactMatch = null;

                        // check built-in commands
                        for (String cmd : shellCommands) {
                            if (cmd.startsWith(input) && !cmd.equals(input)) {
                                tabCompletionMatches.add(cmd);
                                if (exactMatch == null) {
                                    exactMatch = cmd;
                                }
                            }
                        }

                        // Check executables
                        String pathEnv = System.getenv("PATH");
                        if (pathEnv != null && !pathEnv.trim().isEmpty()) {
                            String[] paths = pathEnv.trim().split(":");
                            for (String dir : paths) {
                                File dirFile = new File(dir);
                                if (dirFile.exists() && dirFile.isDirectory()) {
                                    File[] files = dirFile.listFiles();
                                    if (files != null) {
                                        for (File file : files) {
                                            String fileName = file.getName();
                                            if (fileName.startsWith(input) &&
                                                    file.canExecute() &&
                                                    !fileName.equals(input)) {

                                                if (!tabCompletionMatches.contains(fileName)) {
                                                    tabCompletionMatches.add(fileName);
                                                    if (exactMatch == null) {
                                                        exactMatch = fileName;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Handle matches
                        if (tabCompletionMatches.size() == 1) {
                            // Single match
                            System.out.print("\r$ " + exactMatch + " ");
                            buffer = new StringBuilder(exactMatch + " ");
                            tabCompletionMatches.clear();
                            lastKeyWasTab = false;
                        } else if (tabCompletionMatches.size() > 1) {
                            // Multiple matches
                            String commonPrefix = findLongestCommonPrefix(tabCompletionMatches);

                            // use common prefix if longer than current input
                            if (commonPrefix.length() > input.length()) {
                                System.out.print("\r$ " + commonPrefix);
                                buffer = new StringBuilder(commonPrefix);
                                // no space if multiple inputs use same prefix
                                lastKeyWasTab = false;
                            } else {
                                // Multiple matches without a longer common prefix
                                if (!lastKeyWasTab) {
                                    System.out.print('\7');
                                    System.out.flush();
                                    lastKeyWasTab = true;
                                }
                            }
                        } else {
                            // No matches 
                            System.out.print('\7');
                            System.out.flush();
                            lastKeyWasTab = false;
                        }
                    } else if (c == 127 || c == 8) { // Backspace
                        if (buffer.length() > 0) {
                            buffer.deleteCharAt(buffer.length() - 1);
                            System.out.print("\b \b");
                            System.out.flush();
                        }
                        lastKeyWasTab = false;
                    } else {
                        buffer.append((char) c);
                        System.out.print((char) c);
                        System.out.flush();
                        lastKeyWasTab = false;
                    }
                }

                System.out.println();

                String input = buffer.toString().trim();
                if (input.isEmpty()) {
                    continue;
                }

                // Parse command line
                List<String> parts = parseCommandLine(input);
                if (parts.isEmpty()) {
                    continue;
                }

                // Check for output redirection
                List<String> commandWithoutRedirection = new ArrayList<>();
                String outputRedirectPath = null;
                String errorRedirectPath = null;
                boolean appendOutput = false;
                boolean appendError = false;

                for (int i = 0; i < parts.size(); i++) {
                    if ((parts.get(i).equals(">") || parts.get(i).equals("1>")) && i + 1 < parts.size()) {
                        outputRedirectPath = parts.get(i + 1);
                        appendOutput = false;
                        i++; 
                    } else if ((parts.get(i).equals(">>") || parts.get(i).equals("1>>")) && i + 1 < parts.size()) {
                        outputRedirectPath = parts.get(i + 1);
                        appendOutput = true;
                        i++;
                    } else if (parts.get(i).equals("2>") && i + 1 < parts.size()) {
                        errorRedirectPath = parts.get(i + 1);
                        appendError = false;
                        i++; 
                    } else if (parts.get(i).equals("2>>") && i + 1 < parts.size()) {
                        errorRedirectPath = parts.get(i + 1);
                        appendError = true;
                        i++; 
                    } else {
                        commandWithoutRedirection.add(parts.get(i));
                    }
                }

                // filtered list for path
                parts = commandWithoutRedirection;
                String command = parts.get(0);
                String[] commandArgs = parts.toArray(new String[0]);

                // exit
                if (command.equals("exit") && parts.size() == 2 && parts.get(1).equals("0")) {
                    running = false;
                    continue;
                }

                // echo
                else if (command.equals("echo")) {
                    StringBuilder result = new StringBuilder();
                    if (parts.size() > 1) {
                        for (int i = 1; i < parts.size(); i++) {
                            if (i > 1) {
                                result.append(" ");
                            }
                            result.append(parts.get(i));
                        }
                    }

                    // stdout redirection
                    if (outputRedirectPath != null) {
                        File redirectFile = outputRedirectPath.startsWith("/") ? new File(outputRedirectPath)
                                : new File(currentDirectory, outputRedirectPath);

                        // Create parent directories if they don't exist
                        File parentDir = redirectFile.getParentFile();
                        if (parentDir != null) {
                            parentDir.mkdirs();
                        }

                        byte[] content = (result.toString() + "\n").getBytes(StandardCharsets.UTF_8);
                        if (appendOutput && redirectFile.exists()) {
                            Files.write(redirectFile.toPath(), content, StandardOpenOption.APPEND);
                        } else {
                            Files.write(redirectFile.toPath(), content);
                        }
                    } else {
                        System.out.println(result.toString());
                    }

                    // stderr redirection file creation
                    if (errorRedirectPath != null) {
                        File redirectFile = errorRedirectPath.startsWith("/") ? new File(errorRedirectPath)
                                : new File(currentDirectory, errorRedirectPath);

                        File parentDir = redirectFile.getParentFile();
                        if (parentDir != null) {
                            parentDir.mkdirs();
                        }

                        Files.write(redirectFile.toPath(), "".getBytes(StandardCharsets.UTF_8));
                    }
                }

                // pwd
                else if (command.equals("pwd")) {
                    if (outputRedirectPath != null) {
                        File redirectFile = outputRedirectPath.startsWith("/") ? new File(outputRedirectPath)
                                : new File(currentDirectory, outputRedirectPath);

                        File parentDir = redirectFile.getParentFile();
                        if (parentDir != null) {
                            parentDir.mkdirs();
                        }

                        Files.write(redirectFile.toPath(),
                                (currentDirectory.getCanonicalPath() + "\n").getBytes(StandardCharsets.UTF_8));
                    } else {
                        System.out.println(currentDirectory.getCanonicalPath());
                    }
                }

                // cd
                else if (command.equals("cd")) {
                    String arg = parts.size() > 1 ? parts.get(1) : "";
                    File newDir;

                    // Go back to HOME
                    if (arg.equals("~")) {
                        newDir = new File(System.getenv("HOME"));
                    }

                    // absolute path
                    else if (arg.startsWith("/")) {
                        newDir = new File(arg);
                    }

                    // relative path
                    else {
                        newDir = new File(currentDirectory, arg);
                    }

                    if (newDir.exists() && newDir.isDirectory()) {
                        currentDirectory = newDir;
                    } else {
                        System.out.println("cd: " + arg + ": No such file or directory");
                    }
                }

                // type
                else if (command.equals("type")) {
                    String argument = parts.get(1);
                    String pathEnv = System.getenv("PATH");
                    boolean found = false;

                    if (shellCommands.contains(argument)) {
                        if (outputRedirectPath != null) {
                            File redirectFile = outputRedirectPath.startsWith("/") ? new File(outputRedirectPath)
                                    : new File(currentDirectory, outputRedirectPath);

                            File parentDir = redirectFile.getParentFile();
                            if (parentDir != null) {
                                parentDir.mkdirs();
                            }

                            Files.write(redirectFile.toPath(),
                                    (argument + " is a shell builtin\n").getBytes(StandardCharsets.UTF_8));
                        } else {
                            System.out.println(argument + " is a shell builtin");
                        }
                    } else if (pathEnv != null && !pathEnv.trim().isEmpty()) {
                        String[] paths = pathEnv.trim().split(":");
                        for (String dir : paths) {
                            File f = new File(dir, argument);
                            if (f.exists() && f.canExecute()) {
                                String output = argument + " is " + f.getAbsolutePath();
                                if (outputRedirectPath != null) {
                                    File redirectFile = outputRedirectPath.startsWith("/")
                                            ? new File(outputRedirectPath)
                                            : new File(currentDirectory, outputRedirectPath);

                                    File parentDir = redirectFile.getParentFile();
                                    if (parentDir != null) {
                                        parentDir.mkdirs();
                                    }

                                    Files.write(redirectFile.toPath(),
                                            (output + "\n").getBytes(StandardCharsets.UTF_8));
                                } else {
                                    System.out.println(output);
                                }
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            String notFoundMsg = argument + ": not found";
                            if (outputRedirectPath != null) {
                                File redirectFile = outputRedirectPath.startsWith("/") ? new File(outputRedirectPath)
                                        : new File(currentDirectory, outputRedirectPath);

                                File parentDir = redirectFile.getParentFile();
                                if (parentDir != null) {
                                    parentDir.mkdirs();
                                }

                                Files.write(redirectFile.toPath(),
                                        (notFoundMsg + "\n").getBytes(StandardCharsets.UTF_8));
                            } else {
                                System.out.println(notFoundMsg);
                            }
                        }
                    } else {
                        String notFoundMsg = argument + ": not found";
                        if (outputRedirectPath != null) {
                            File redirectFile = outputRedirectPath.startsWith("/") ? new File(outputRedirectPath)
                                    : new File(currentDirectory, outputRedirectPath);

                            File parentDir = redirectFile.getParentFile();
                            if (parentDir != null) {
                                parentDir.mkdirs();
                            }

                            Files.write(redirectFile.toPath(), (notFoundMsg + "\n").getBytes(StandardCharsets.UTF_8));
                        } else {
                            System.out.println(notFoundMsg);
                        }
                    }
                }

                // cat command
                else if (command.equals("cat")) {
                    StringBuilder output = new StringBuilder();
                    StringBuilder errorOutput = new StringBuilder();
                    boolean hasError = false;

                    for (int i = 1; i < parts.size(); i++) {
                        String filePath = parts.get(i);
                        File fileToRead = filePath.startsWith("/") ? new File(filePath)
                                : new File(currentDirectory, filePath);

                        if (!fileToRead.exists() || !fileToRead.isFile()) {
                            String errorMsg = "cat: " + filePath + ": No such file or directory";
                            if (errorRedirectPath != null) {
                                errorOutput.append(errorMsg).append("\n");
                            } else {
                                System.err.println(errorMsg);
                            }
                            hasError = true;
                            continue;
                        }

                        String content = new String(Files.readAllBytes(fileToRead.toPath()), StandardCharsets.UTF_8);
                        output.append(content);
                    }

                    // output redirection
                    if (outputRedirectPath != null) {
                        File redirectFile = outputRedirectPath.startsWith("/") ? new File(outputRedirectPath)
                                : new File(currentDirectory, outputRedirectPath);

                        File parentDir = redirectFile.getParentFile();
                        if (parentDir != null) {
                            parentDir.mkdirs();
                        }

                        byte[] content = output.toString().getBytes(StandardCharsets.UTF_8);
                        if (appendOutput && redirectFile.exists()) {
                            Files.write(redirectFile.toPath(), content, StandardOpenOption.APPEND);
                        } else {
                            Files.write(redirectFile.toPath(), content);
                        }
                    } else if (!hasError || output.length() > 0) {
                        System.out.print(output.toString());
                    }

                    // error redirection
                    if (errorRedirectPath != null && errorOutput.length() > 0) {
                        File redirectFile = errorRedirectPath.startsWith("/") ? new File(errorRedirectPath)
                                : new File(currentDirectory, errorRedirectPath);

                        File parentDir = redirectFile.getParentFile();
                        if (parentDir != null) {
                            parentDir.mkdirs();
                        }

                        byte[] content = errorOutput.toString().getBytes(StandardCharsets.UTF_8);
                        if (appendError && redirectFile.exists()) {
                            Files.write(redirectFile.toPath(), content, StandardOpenOption.APPEND);
                        } else {
                            Files.write(redirectFile.toPath(), content);
                        }
                    }
                }

                // Other commands
                else {
                    String pathEnv = System.getenv("PATH");
                    boolean found = false;

                    if (pathEnv != null && !pathEnv.trim().isEmpty()) {
                        String[] paths = pathEnv.trim().split(":");
                        for (String dir : paths) {
                            File f = new File(dir, command);
                            if (f.exists() && f.canExecute()) {
                                ProcessBuilder pb = new ProcessBuilder(commandArgs);

                                // Redirections
                                if (outputRedirectPath != null) {
                                    File redirectFile = outputRedirectPath.startsWith("/")
                                            ? new File(outputRedirectPath)
                                            : new File(currentDirectory, outputRedirectPath);

                                    File parentDir = redirectFile.getParentFile();
                                    if (parentDir != null) {
                                        parentDir.mkdirs();
                                    }

                                    if (appendOutput && redirectFile.exists()) {
                                        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                                        Process process = pb.start();

                                        // Read output and append to file
                                        String output = new String(process.getInputStream().readAllBytes(),
                                                StandardCharsets.UTF_8);
                                        Files.write(redirectFile.toPath(), output.getBytes(StandardCharsets.UTF_8),
                                                StandardOpenOption.APPEND);

                                        process.waitFor();
                                    } else {
                                        pb.redirectOutput(redirectFile);
                                        if (errorRedirectPath == null) {
                                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                                        }
                                        pb.start().waitFor();
                                    }
                                } else {
                                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                                    if (errorRedirectPath == null) {
                                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                                    }
                                    pb.start().waitFor();
                                }

                                // seperate error redirection 
                                if (errorRedirectPath != null) {
                                    File redirectFile = errorRedirectPath.startsWith("/") ? new File(errorRedirectPath)
                                            : new File(currentDirectory, errorRedirectPath);

                                    File parentDir = redirectFile.getParentFile();
                                    if (parentDir != null) {
                                        parentDir.mkdirs();
                                    }

                                    // stderr separate
                                    ProcessBuilder errPb = new ProcessBuilder(commandArgs);
                                    errPb.redirectOutput(ProcessBuilder.Redirect.PIPE);

                                    if (appendError && redirectFile.exists()) {
                                        errPb.redirectError(ProcessBuilder.Redirect.PIPE);
                                        Process process = errPb.start();

                                        // Read error and append to file
                                        String error = new String(process.getErrorStream().readAllBytes(),
                                                StandardCharsets.UTF_8);
                                        Files.write(redirectFile.toPath(), error.getBytes(StandardCharsets.UTF_8),
                                                StandardOpenOption.APPEND);

                                        process.waitFor();
                                    } else {
                                        errPb.redirectError(redirectFile);
                                        errPb.start().waitFor();
                                    }
                                }

                                found = true;
                                break;
                            }
                        }
                    }

                    if (!found) {
                        System.out.println(command + ": command not found");
                    }
                }
            }
        } finally {
            // Restore settings
            ProcessBuilder restorePb = new ProcessBuilder("/bin/bash", "-c", "stty echo icanon");
            restorePb.inheritIO();
            restorePb.start().waitFor();
        }
    }

    /**
     * Parse a command line string into tokens, respecting quotes and escapes
     */
    private static List<String> parseCommandLine(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            // Handle escape character (\)
            if (c == '\\' && i < input.length() - 1) {
                char nextChar = input.charAt(i + 1);

                // single quotes -> literal backslashes
                if (inSingleQuote) {
                    currentToken.append('\\');
                } else if (inDoubleQuote) {
                    if (nextChar == '"') {
                        currentToken.append('"');
                        i++;
                    } else if (nextChar == '\\') {
                        currentToken.append('\\');
                        i++;
                    } else if (nextChar == '\'') {
                        currentToken.append('\\');
                        currentToken.append('\'');
                        i++;
                    } else {
                        currentToken.append(c);
                        currentToken.append(nextChar);
                        i++;
                    }
                } else {
                    currentToken.append(nextChar);
                    i++;
                }
                continue;
            }

            // Handle single and double quotes
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }

            if (c == ' ' && !inSingleQuote && !inDoubleQuote) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                }
            } else {
                currentToken.append(c);
            }
        }

        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }

    /**
     * Find the longest common prefix among a list of strings
     */
    private static String findLongestCommonPrefix(List<String> strings) {
        if (strings.isEmpty()) {
            return "";
        }

        String prefix = strings.get(0);
        for (String str : strings) {
            while (!str.startsWith(prefix)) {
                prefix = prefix.substring(0, prefix.length() - 1);
                if (prefix.isEmpty()) {
                    return "";
                }
            }
        }
        return prefix;
    }
}
