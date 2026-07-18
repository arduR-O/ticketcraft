<RULE[ticketcraft_architecture]>
### TicketCraft Architectural Guidelines

When working on this codebase, always adhere to the following rules:

1. **Retain the Big Picture**: Do not hyper-fixate on isolated segments of code. Always maintain awareness of the end-to-end user journey.
2. **Strict Adherence to the Implementation Plan**: The `notes/implementation_plan.md` is the definitive source of truth for all architectural decisions. You must thoroughly consult it before designing or implementing any new features to ensure alignment with the established contracts.
3. **Cross-Service Cohesion**: Keep in mind that all microservices in this project are highly interconnected. Before modifying a service, verify how the change impacts REST, gRPC, and Kafka contracts across the entire ecosystem. Ensure that all data models and event structures perfectly match across producers and consumers.
4. **Avoid Siloed Implementations**: Do not make assumptions about data flowing in or out of a service. Trace the data flow back to the gateway, the catalog database, or the asynchronous queues to guarantee consistency.
5. **Detailed Walkthroughs & Committing**: When completing a phase or major task, always write a detailed walkthrough in a separate markdown file in the `notes/` directory (e.g., `notes/walkthrough_phase_4.md`) that includes both the **general flow** and **file-by-file changes**. Crucially, format all file paths as standard relative markdown links (e.g., `[booking-service/pom.xml](../booking-service/pom.xml)`) so they can be followed via `gx` in Vim. After verifying the implementation, always `git commit` and `git push` to remote to persist the milestone.
6. **Codebase Readability**: Add docstrings to all functions in the codebase. Crucially, these docstrings must explain **"why"** the function does what it does, rather than just **"what"** it does, to preserve architectural intent.
</RULE[ticketcraft_architecture]>

<RULE[frontend_contract]>
7. **Frontend Design Driven by Backend Contracts**: When creating the frontend, explicitly look at what the server requires (e.g., specific headers, exact query params, and required authentication/authorization tokens) and returns (the exact DTOs), and design the frontend implementation accordingly. Do not assume endpoint shapes or auth requirements without verifying the backend controllers and gateway security configs.
</RULE[frontend_contract]>

<RULE[next_mcp_server]>
8. **Next.js MCP Server Tools**: When working on the Next.js frontend, actively use the `next-devtools-mcp` tools (such as `get_errors`, `get_logs`, `get_page_metadata`, `get_routes`) to debug, diagnose errors, and consult the Next.js knowledge base. This guarantees alignment with Next.js 16 app router best practices and prevents hallucinations.
</RULE[next_mcp_server]>
