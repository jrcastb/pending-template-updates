# Diagrams

Companion to [DESIGN.md](DESIGN.md). Green boxes are new; grey boxes are existing systems.

## 1 Architecture

```mermaid
flowchart LR
    subgraph TP["Template plane: global, shared by all firms"]
        TS["Template store"]:::existing
        PH["Publish hook"]:::new
        BUS["Event bus"]:::new
        DIFF["JSON diff"]:::existing
        SUM["Summary service and cache"]:::new
        TS --> PH --> BUS
        TS --> DIFF --> SUM
    end

    subgraph REG["Firm's region: residency boundary"]
        LT["Latest-version table, regional replica"]:::new
        MET["CloudWatch (existing) — new metrics & alarms"]:::existing
        subgraph FIRM["Firm boundary: customer-specific database"]
            ES["Engagement system, with new snapshot hooks"]:::existing
            BC["Binding consumer"]:::new
            DB[("Bindings and decision log: new tables in the existing firm database")]:::new
            AC["Affected-count consumer"]:::new
            API["Engagement list API, extended"]:::existing
            ES -->|"snapshots, decisions"| BC --> DB
            DB --> AC
            DB --> API
        end
    end

    U(("User")) --> API
    BUS -->|"latest version"| LT
    BUS -->|"TemplatePublished"| AC
    LT -->|"latest version"| API
    SUM -->|"summaries"| API
    AC -->|"counts only, no IDs"| MET

    classDef new fill:#d8f3dc,stroke:#2d6a4f,color:#000
    classDef existing fill:#e9ecef,stroke:#6c757d,color:#000
```

Only template data (latest versions, publish events, summaries) enters the firm boundary, and only
counts leave it. Bindings and decisions stay in the firm's database and region.

## 2 Publish to visible status

```mermaid
sequenceDiagram
    autonumber
    actor CT as Content team
    participant TS as Template store
    participant BUS as Event bus
    participant LT as Latest-version table (region)
    participant AC as Affected-count consumer (per firm)
    participant DB as Firm database (bindings)
    participant MET as Metrics
    actor U as User
    participant API as Engagement list API

    CT->>TS: publish template T, version n+1
    TS->>BUS: TemplatePublished(T, n+1, publishId)
    BUS->>LT: write if newer (max version)
    BUS->>AC: TemplatePublished
    AC->>DB: findByTemplate(scope, T)
    DB-->>AC: bindings
    AC->>MET: affected, checking (always, even 0/0)
    Note over CT,MET: One record per template, one report per firm. Nothing is written per engagement.

    U->>API: open engagement list (FirmScope from token)
    API->>DB: bindings for the firm
    API->>LT: latest version per template
    API->>API: PendingRule: latest > max(applied, lastDeclined)
    API-->>U: UPDATE_AVAILABLE, UP_TO_DATE or CHECKING
    Note over U,API: No engagement is loaded on this path.
```

A publish writes one record per template and one count per firm; nothing is written per
engagement. When the user opens the list, status is derived from bindings and the latest version.
