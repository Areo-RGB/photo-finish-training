---
description: "Activate the current workspace folder as the active Serena project"
name: "Activate Serena Project"
argument-hint: "Optional project path (defaults to current folder)"
agent: "agent"
---
Activate the project in Serena.

Inputs:
- Optional argument: project path. If no argument is provided, use the current working directory.

Steps:
1. Resolve the target path.
2. Activate that path as the current Serena project.
3. Confirm activation by reporting the resolved project root Serena is using.
4. If activation fails, explain the exact error and suggest the shortest fix.

Output format:
- Activated: yes/no
- Project root: <absolute path>
- Notes: <one short line>
