package io.gitlab.teamproject2026.config.interpreter;

import java.util.List;
import java.util.StringJoiner;

/**
 * Immutable node in the configuration abstract syntax tree (AST),
 * representing a statement with its arguments, nested child statements,
 * and source line number.
 */
public record ConfNode(String name, List<String> args, List<ConfNode> children, int lineNumber ) {
    public ConfNode {
        args =  List.copyOf(args);
        children = List.copyOf(children);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        buildString(sb, "", true);
        return sb.toString();
    }

    private void buildString(StringBuilder sb, String prefix, boolean isLast) {
        sb.append(prefix);
        if (!prefix.isEmpty()) {
            sb.append(isLast ? "└── " : "├── ");
        }
        sb.append(name);
        if (!args.isEmpty()) {
            StringJoiner joiner = new StringJoiner(", ", " [", "]");
            for (String arg : args) {
                joiner.add(arg);
            }
            sb.append(joiner);
        }
        sb.append('\n');
        for (int i = 0; i < children.size(); i++) {
            boolean childIsLast = (i == children.size() - 1);
            children.get(i).buildString( sb, prefix + (isLast ? "    " : "│   "), childIsLast );
        }
    }
}
