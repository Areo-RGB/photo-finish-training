---
description: "Navigate the repository with Serena and CodeGraphContext before reading code"
name: "Codebase Navigation"
argument-hint: "Optional task, question, or target behavior"
agent: "agent"
---
Use this prompt when you need to locate, inspect, or explain code in this workspace.

Inputs:
- Optional argument: a task, question, or target behavior.
- If no argument is provided, infer the goal from the active editor context.

Steps:
1. Activate the current project as the Serena project.
2. Use CodeGraphContext MCP first to identify likely files, symbols, and relationships.
3. Prefer graph-backed recommendations over broad recursive searching.
4. Read only the minimum nearby code needed to answer the task or make the next change.
5. If the task is still ambiguous after graph lookup, ask one focused clarification.
6. Summarize the relevant files, key symbols, and the next concrete action.

Output format:
