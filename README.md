![StudentHack Winner](https://img.shields.io/badge/StudentHack2025-Winner-blueviolet)

# Java Shell

A lightweight shell implementation written in Java, developed for the StudentHack 2025 hackathon (hosted by the University of Manchester), where it secured 1st place in the individual challenge.
While Java isn't the conventional choice for shell development, this project showcases how it can be leveraged to build a functional, responsive, and feature-rich command-line interface from scratch.

## Features

- Basic command execution with support for external programs
- Output redirection (`>`, `>>`, `2>`, `2>>`)
- Tab autocompletion for commands and file paths
- Built-in commands:
  - `echo`: Print text to standard output
  - `pwd`: Print working directory
  - `cd`: Change directory
  - `type`: Display command type information
  - `exit`: Exit the shell
- Command history navigation
- Support for command arguments and options

## Running the Shell

1. Ensure you have Java 11 or later installed
2. Clone this repository
3. Compile the project:
   ```bash
   javac Main.java
   ```
4. Run the shell:
   ```bash
   java Main
   ```

## Planned Improvements

### Code Refactoring

- [ ] Improve code organization and modularity
- [ ] Implement better error handling patterns
- [ ] Enhance code documentation
- [ ] Optimize performance-critical sections
- [ ] Add comprehensive test suite (planned for future updates)

### New Features

- [ ] Support for command pipelines (`|`)
- [ ] Environment variable management
- [ ] Background process execution
- [ ] Custom prompt configuration
- [ ] Command aliases
- [ ] Script execution support
- [ ] Enhanced error handling and reporting

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
