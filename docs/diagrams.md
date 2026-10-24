# Diagrams

Companion to [DESIGN.md](DESIGN.md). Green boxes are new; grey boxes are existing systems; the
dashed box is optional.

## 1 Architecture

```mermaid
flowchart LR
    subgraph TP["Template plane: global, shared by all firms"]
        TS["Template store"]:::existing
        PH["Publish hook + outbox"]:::new
        BUS["Event bus (EventBridge)"]:::new
        SUM["Summary service and cache"]:::new
        DIFF["JSON diff"]:::existing
        LLM["LLM narrative (Bedrock), optional v2"]:::optional
        TS --> PH --> BUS
        BUS -->|"TemplatePublished"| SUM
        SUM --> DIFF
        SUM -.-> LLM
    end

    subgraph REG["Firm's region: residency boundary"]
        Q["Regional queue (SQS + DLQ)"]:::new
        LT["Latest-version table"]:::new
        MET["CloudWatch (existing): new metrics & alarms"]:::existing

        subgraph TRK["Tracking service: regional, multi-tenant"]
            BC["Event consumer"]:::new
            AC["Affected-count job"]:::new
            PAPI["Pending-updates API"]:::new
            subgraph SCH["Schema per firm, owned by a per-firm role (Aurora Postgres)"]
                DB[("Bindings, engagement registry, decision log")]:::new
            end
        end

        subgraph FIRM["Engagement system (per firm, unchanged storage)"]
            ES["Engagement system + hooks with outbox"]:::existing
            BF["Backfill worker"]:::new
            API["Engagement list API, extended"]:::existing
        end
    end

    U(("User")) --> API
    BUS --> Q
    ES -->|"created, opened, applied, declined, archived"| Q
    BF -->|"load existing"| ES
    BF -->|"engagement IDs (once)"| Q
    Q --> BC
    BC -->|"latest version"| LT
    BC -->|"bindings, registry, decisions"| DB
    BC -->|"TemplatePublished"| AC
    AC -->|"every enabled firm"| DB
    AC -->|"counts only, no IDs"| MET
    API -->|"user token"| PAPI
    PAPI --> DB
    PAPI --> LT
    PAPI -->|"templateId, from, to"| SUM

    classDef new fill:#d8f3dc,stroke:#2d6a4f,color:#000
    classDef existing fill:#e9ecef,stroke:#6c757d,color:#000
    classDef optional fill:#ffffff,stroke:#6c757d,stroke-dasharray:5 5,color:#000
```

Only template data (publish events, summaries) crosses into the region from the global plane.
Out of it go only counts to metrics and summary requests carrying template IDs and versions.
Bindings, the registry and decisions stay in the firm's schema, in the firm's region; every
transaction on firm data runs as that firm's role.

## 2 Publish to visible status

```mermaid
sequenceDiagram
    autonumber
    actor CT as Content team
    participant TS as Template store
    participant Q as Regional queue
    participant BC as Event consumer
    participant LT as Latest-version table (region)
    participant AC as Affected-count job
    participant DB as Tracking DB (firm schema)
    participant MET as Metrics
    actor U as User
    participant API as Engagement list API
    participant PAPI as Pending-updates API

    CT->>TS: publish template T, version n+1
    TS->>Q: TemplatePublished(T, n+1, publishId), via outbox and bus
    Q->>BC: TemplatePublished
    BC->>LT: write if newer (max version)
    BC->>AC: TemplatePublished
    loop every enabled firm
        AC->>DB: as firm role, findByTemplate + registry
        DB-->>AC: bindings, registered IDs
        AC->>MET: affected, checking (always, even 0/0)
    end
    Note over CT,MET: One record per template, one report per firm. Nothing is written per engagement.

    U->>API: open engagement list
    API->>PAPI: statuses (user token)
    PAPI->>DB: as firm role from token, bindings
    PAPI->>LT: latest version per template
    PAPI->>PAPI: PendingRule: latest > max(applied, lastDeclined)
    PAPI-->>API: UPDATE_AVAILABLE, UP_TO_DATE or CHECKING
    API-->>U: list with status
    Note over U,PAPI: No engagement is loaded on this path.
```

A publish writes one record per template and one count per firm; nothing is written per
engagement. When the user opens the list, status is derived from the firm's bindings and the
latest version.
